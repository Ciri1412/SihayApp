#include "esp_camera.h"
#include <WiFi.h>
#include <esp_wifi.h> // ADDED: Required for WIFI_IF_STA
#include <esp_now.h>
#include <WiFiUdp.h>
#include <WebServer.h>
#include "soc/soc.h"
#include "soc/rtc_cntl_reg.h"

// ===================
// Select camera model
// ===================
#define CAMERA_MODEL_AI_THINKER // Set to AI Thinker
#include "camera_pins.h"

// ===========================
// WIFI CREDENTIALS
// ===========================
const char* ssid     = "Test";
const char* password = "12345678";

// ===========================
// ESP-NOW: CONTROLLER PEER
// ===========================
// Your exact ESP32-S3 MAC Address
uint8_t controllerMac[] = { 0xD4, 0xD4, 0xDA, 0x13, 0xDC, 0xBC };

// Result packet sent to the controller
typedef struct {
  char status[16];   // "FERTILE", "INFERTILE", "NON_EGG", "UNCLEAR", "ERROR"
  char action[8];    // "LEFT", "RIGHT", "NONE"
  float confidence;  // 0.0 - 1.0
} __attribute__((packed)) EggResultMessage;

// ===========================
// GLOBAL OBJECTS
// ===========================
WiFiUDP udp;
WebServer server(80);

const unsigned int udpPort = 8888;
String androidServerIp = "";

// ===========================
// FUNCTION DECLARATIONS
// ===========================
void startCameraServer();
void checkUdpDiscovery();
void handleConfig();
void captureAndSend();
void onDataSent(const uint8_t *mac_addr, esp_now_send_status_t status);
String extractJsonValue(const String& json, const char* key);
bool waitForAppServerReady(uint32_t timeoutMs);

// ===========================
// SETUP
// ===========================
void setup() {
  WRITE_PERI_REG(RTC_CNTL_BROWN_OUT_REG, 0); // Disable brownout

  Serial.begin(115200);
  Serial.setDebugOutput(true);
  Serial.println();

  // 1. CAMERA CONFIGURATION
  camera_config_t config;
  config.ledc_channel = LEDC_CHANNEL_0;
  config.ledc_timer = LEDC_TIMER_0;
  config.pin_d0 = Y2_GPIO_NUM;
  config.pin_d1 = Y3_GPIO_NUM;
  config.pin_d2 = Y4_GPIO_NUM;
  config.pin_d3 = Y5_GPIO_NUM;
  config.pin_d4 = Y6_GPIO_NUM;
  config.pin_d5 = Y7_GPIO_NUM;
  config.pin_d6 = Y8_GPIO_NUM;
  config.pin_d7 = Y9_GPIO_NUM;
  config.pin_xclk = XCLK_GPIO_NUM;
  config.pin_pclk = PCLK_GPIO_NUM;
  config.pin_vsync = VSYNC_GPIO_NUM;
  config.pin_href = HREF_GPIO_NUM;
  config.pin_sccb_sda = SIOD_GPIO_NUM;
  config.pin_sccb_scl = SIOC_GPIO_NUM;
  config.pin_pwdn = PWDN_GPIO_NUM;
  config.pin_reset = RESET_GPIO_NUM;
  config.xclk_freq_hz = 20000000;
  
  config.frame_size = FRAMESIZE_SVGA;  
  config.pixel_format = PIXFORMAT_JPEG; 
  config.grab_mode = CAMERA_GRAB_LATEST; 
  config.fb_location = CAMERA_FB_IN_DRAM; 
  config.jpeg_quality = 10; 
  config.fb_count = 1; 

  esp_err_t err = esp_camera_init(&config);
  if (err != ESP_OK) {
    Serial.printf("Camera init failed with error 0x%x", err);
    return;
  }

  // ==========================================
  // 📸 SENSOR SETTINGS (MAPPED TO YOUR SCREENSHOT)
  // ==========================================
  sensor_t * s = esp_camera_sensor_get();
  if (s != NULL) {
    s->set_brightness(s, 0);
    s->set_contrast(s, 0);
    s->set_saturation(s, 0);
    s->set_special_effect(s, 0);
    s->set_whitebal(s, 1);                   // AWB Enable
    s->set_awb_gain(s, 1);                   // AWB Gain Enable
    s->set_wb_mode(s, 0);                    // WB Mode: Auto
    s->set_exposure_ctrl(s, 1);              // AEC Sensor
    s->set_aec2(s, 1);                       // AEC DSP
    s->set_ae_level(s, 0);                   // AE Level
    s->set_aec_value(s, 300);                // AEC Value
    s->set_gain_ctrl(s, 1);                  // AGC
    s->set_agc_gain(s, 0);                   // AGC Gain
    s->set_gainceiling(s, (gainceiling_t)0); // Gain Ceiling (0 = 2x)
    s->set_bpc(s, 1);                        // BPC
    s->set_wpc(s, 1);                        // WPC
    s->set_raw_gma(s, 0);                    // Raw GMA (UNCHECKED - Disabled)
    s->set_lenc(s, 1);                       // Lens Correction (Checked)
    s->set_hmirror(s, 0);                    // H-Mirror
    s->set_vflip(s, 0);                      // V-Flip
    s->set_dcw(s, 1);                        // DCW (Downsize EN)
    s->set_colorbar(s, 0);                   // Color Bar
  }

  // 2. WIFI CONNECTION (STA MODE)
  WiFi.mode(WIFI_STA);         
  WiFi.begin(ssid, password);
  WiFi.setSleep(false); // Prevent radio sleep
  WiFi.setTxPower(WIFI_POWER_19_5dBm);

  Serial.print("Connecting to WiFi");
  while (WiFi.status() != WL_CONNECTED) {
    delay(500);
    Serial.print(".");
  }
  Serial.println("\nWiFi Connected");
  Serial.print("ESP32 IP: ");
  Serial.println(WiFi.localIP());
  Serial.printf("WiFi Channel: %d\n", WiFi.channel()); // DEBUG: Verify Channel

  // 3. INIT ESP-NOW FOR CONTROLLER COMMUNICATION
  if (esp_now_init() != ESP_OK) {
    Serial.println("Error initializing ESP-NOW");
  } else {
    esp_now_register_send_cb((esp_now_send_cb_t)onDataSent);
    
    esp_now_peer_info_t peerInfo = {};
    memcpy(peerInfo.peer_addr, controllerMac, 6);
    peerInfo.channel = 0;      
    peerInfo.encrypt = false;
    peerInfo.ifidx = WIFI_IF_STA; // FIX: Force peer to use Station Interface

    if (esp_now_add_peer(&peerInfo) != ESP_OK) {
      Serial.println("Failed to add ESP-NOW peer (controller)");
    } else {
      Serial.println("ESP-NOW peer (controller) added");
    }
  }

  // 4. START DISCOVERY & SERVER
  udp.begin(udpPort);
  
  server.on("/config", HTTP_GET, handleConfig);
  server.on("/capture", HTTP_GET, [](){
      server.send(200, "text/plain", "Capturing and sending to Android...");
      captureAndSend();
  });
  server.on("/", HTTP_GET, []() {
    server.send(200, "text/plain", "Sihay ESP32 Ready. Android IP: " + androidServerIp);
  });

  server.begin();
  Serial.println("Web Server Started");
}

void loop() {
  server.handleClient();
  checkUdpDiscovery();

  // --- AUTO TEST MODE ---
  static unsigned long lastCaptureTime = 0;
  // CHANGED FROM 15000 TO 8000 FOR AN 8-SECOND DELAY
  if (androidServerIp != "" && millis() - lastCaptureTime > 8000) {
    Serial.println("--- TEST MODE: Sending Photo ---");
    captureAndSend();
    lastCaptureTime = millis();
  }
}

// ===========================
// HELPER FUNCTIONS
// ===========================

void onDataSent(const uint8_t *mac_addr, esp_now_send_status_t status) {
  Serial.print("\r\nLast ESP-NOW Send Status:\t");
  Serial.println(status == ESP_NOW_SEND_SUCCESS ? "Delivery Success" : "Delivery Fail");
}

void checkUdpDiscovery() {
  int packetSize = udp.parsePacket();
  if (packetSize) {
    char incomingPacket[255];
    int len = udp.read(incomingPacket, 255);
    if (len > 0) incomingPacket[len] = 0;

    String message = String(incomingPacket);
    if (message.indexOf("SIHAY_DISCOVER") >= 0) {
      udp.beginPacket(udp.remoteIP(), udp.remotePort());
      udp.print("SIHAY_HERE"); 
      udp.endPacket();
    }
  }
}

void handleConfig() {
  if (server.hasArg("server_ip")) {
    androidServerIp = server.arg("server_ip");
    Serial.println("CONNECTED TO APP! Phone IP: " + androidServerIp);
    server.send(200, "text/plain", "OK");
  } else {
    server.send(400, "text/plain", "Missing IP");
  }
}

bool waitForAppServerReady(uint32_t timeoutMs) {
  if (androidServerIp == "") return false;

  uint32_t start = millis();
  while (millis() - start < timeoutMs) {
    WiFiClient pingClient;
    pingClient.setTimeout(1500);

    if (pingClient.connect(androidServerIp.c_str(), 8080)) {
      pingClient.println("GET /ping HTTP/1.1");
      pingClient.println("Host: " + androidServerIp + ":8080");
      pingClient.println("Connection: close");
      pingClient.println();

      uint32_t t0 = millis();
      while (millis() - t0 < 1500) {
        if (pingClient.available()) {
          String statusLine = pingClient.readStringUntil('\n');
          statusLine.trim();
          pingClient.stop();
          if (statusLine.indexOf("200") >= 0) {
            return true;
          }
          break;
        }
        delay(10);
      }
      pingClient.stop();
    }
    delay(250);
  }
  return false;
}

String extractJsonValue(const String& json, const char* key) {
  String pattern = "\"" + String(key) + "\":";
  int idx = json.indexOf(pattern);
  if (idx < 0) return "";

  idx += pattern.length();
  while (idx < json.length() && json[idx] == ' ') idx++;

  if (idx < json.length() && json[idx] == '\"') {
    idx++; 
    int end = json.indexOf('\"', idx);
    if (end < 0) return "";
    return json.substring(idx, end);
  }

  int end = idx;
  while (end < json.length() && json[end] != ',' && json[end] != '}') end++;
  String raw = json.substring(idx, end);
  raw.trim();
  return raw;
}

void captureAndSend() {
  if (androidServerIp == "") {
    Serial.println("Error: No Android Phone connected yet.");
    return;
  }

  if (!waitForAppServerReady(5000)) {
    Serial.println("❌ App server not reachable on http://" + androidServerIp + ":8080 (check phone IP / WiFi).");
    return;
  }

  camera_fb_t * fb = esp_camera_fb_get();
  if (!fb) {
    Serial.println("Camera capture failed");
    return;
  }

  Serial.println("\n----------------------------------");
  Serial.println("📷 Took Photo! Size: " + String(fb->len) + " bytes");
  Serial.println("🚀 Sending photo to App at IP: " + androidServerIp);

  WiFiClient client;
  client.setTimeout(12000);
  String jsonLine = "";  

  if (client.connect(androidServerIp.c_str(), 8080)) {
    String boundary = "Esp32Boundary";
    String head = "--" + boundary +
      "\r\nContent-Disposition: form-data; name=\"image\"; filename=\"esp32_cam.jpg\""
      "\r\nContent-Type: image/jpeg\r\n\r\n";
    String tail = "\r\n--" + boundary + "--\r\n";

    uint32_t totalLen = fb->len + head.length() + tail.length();

    client.println("POST /analyze HTTP/1.1");
    client.println("Host: " + androidServerIp + ":8080");
    client.println("Content-Type: multipart/form-data; boundary=" + boundary);
    client.println("Content-Length: " + String(totalLen));
    client.println("Connection: close");
    client.println(); 
    client.print(head);

    uint8_t *fbBuf = fb->buf;
    size_t fbLen = fb->len;
    size_t chunk_size = 1024;
    for (size_t i = 0; i < fbLen; i += chunk_size) {
      size_t to_send = (fbLen - i < chunk_size) ? (fbLen - i) : chunk_size;
      client.write(fbBuf + i, to_send);
    }
    client.print(tail);

    Serial.println("⏳ Waiting for App to analyze...");
    long timeout = millis();
    while (millis() - timeout < 12000) { 
      if (client.available()) {
        String line = client.readStringUntil('\n');
        line.trim();
        
        if(line.length() > 0) {
           Serial.println("APP SAYS: " + line);
        }
        
        if (line.startsWith("{")) { 
          jsonLine = line;
        }
      }
      delay(5);
    }
    client.stop();
    Serial.println("✅ Connection closed.");
    Serial.println("----------------------------------\n");

  } else {
    Serial.println("❌ Connection to Android Failed. Is the app server actually running?");
  }

  esp_camera_fb_return(fb);

  if (jsonLine.length() > 0) {
    String statusStr     = extractJsonValue(jsonLine, "status");
    String actionStr     = extractJsonValue(jsonLine, "action");
    String confidenceStr = extractJsonValue(jsonLine, "confidence");
    float confidenceVal  = confidenceStr.toFloat();

    EggResultMessage msg;
    statusStr.substring(0, sizeof(msg.status) - 1).toCharArray(msg.status, sizeof(msg.status));
    actionStr.substring(0, sizeof(msg.action) - 1).toCharArray(msg.action, sizeof(msg.action));
    msg.confidence = confidenceVal;

    esp_err_t result = esp_now_send(controllerMac, (uint8_t *)&msg, sizeof(msg));
    if (result == ESP_OK) {
      Serial.println("📡 ESP-NOW: Result successfully sent to Motor Controller!");
    } else {
      Serial.printf("❌ ESP-NOW send error: %d\n", result);
    }
  }
}