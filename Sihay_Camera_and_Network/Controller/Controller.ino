#include <WiFi.h>
#include <esp_wifi.h> // ADDED: Required for deep WiFi settings
#include <esp_now.h>
#include <ESP32Servo.h>
#include <Wire.h>
#include <LiquidCrystal_I2C.h>

// ===========================
// WIFI CREDENTIALS
// ===========================

const char* ssid     = "Test";
const char* password = "12345678";

// ===========================
// PIN DEFINITIONS
// ===========================

#define ENB             19
#define IN3             16
#define IN4             17

#define SERVO_FERTILE   13
#define SERVO_INFERTILE 23
#define SERVO_HOLDER    5

#define IR_SENSOR_PIN   32

#define I2C_SDA         26
#define I2C_SCL         27

// ===========================
// CONSTANTS
// ===========================

#define PWM_FREQ        5000
#define PWM_RESOLUTION  8
#define MOTOR_SPEED     180

#define HOLDER_OPEN     60
#define HOLDER_CLOSE    135

#define GATE_IDLE_LEFT  15
#define GATE_OPEN_LEFT  60

#define GATE_IDLE       0
#define GATE_OPEN       65

#define BOOT_MOTOR_DIRECTION  1     // 1 = forward, 0 = backward

// ===========================
// GLOBALS
// ===========================

LiquidCrystal_I2C lcd(0x27, 16, 2);

Servo fertileServo;
Servo infertileServo;
Servo holderServo;

String currentActiveGate = "";

typedef struct {
  char  status[16];
  char  action[8];
  float confidence;
} __attribute__((packed)) EggResultMessage;

EggResultMessage lastMsg;

// ===========================
// LCD HELPER
// ===========================

void lcdPrint(String row0, String row1 = "") {
  lcd.clear();

  // Center row0
  int pad0 = (16 - row0.length()) / 2;
  lcd.setCursor(pad0 > 0 ? pad0 : 0, 0);
  lcd.print(row0);

  // Center row1
  if (row1 != "") {
    int pad1 = (16 - row1.length()) / 2;
    lcd.setCursor(pad1 > 0 ? pad1 : 0, 1);
    lcd.print(row1);
  }

  Serial.println("[LCD] " + row0 + (row1 != "" ? " | " + row1 : ""));
}

// ===========================
// SERVO HELPERS
// ===========================

void fertileWrite(int angle) {
  fertileServo.write(angle);
  delay(600);
  Serial.println("[FERTILE] Angle: " + String(angle));
}

void infertileWrite(int angle) {
  infertileServo.write(angle);
  delay(600);
  Serial.println("[INFERTILE] Angle: " + String(angle));
}

void holderWrite(int angle) {
  holderServo.write(angle);
  delay(400);
  Serial.println("[HOLDER] Angle: " + String(angle));
}

// ===========================
// MOTOR CONTROL
// ===========================

void motorForward() {
  digitalWrite(IN3, HIGH);
  digitalWrite(IN4, LOW);
  ledcWrite(ENB, MOTOR_SPEED);
  Serial.println("[MOTOR] Forward");
}

void motorStop() {
  digitalWrite(IN3, LOW);
  digitalWrite(IN4, LOW);
  ledcWrite(ENB, 0);
  Serial.println("[MOTOR] Stop");
}

// ===========================
// GATE CONTROL
// ===========================

void setGate(String action) {
  if (currentActiveGate == "LEFT") {
    fertileWrite(GATE_IDLE_LEFT);
    Serial.println("[GATE] Closing fertile -> " + String(GATE_IDLE_LEFT));
  }
  if (currentActiveGate == "RIGHT") {
    infertileWrite(GATE_IDLE);
    Serial.println("[GATE] Closing infertile -> " + String(GATE_IDLE));
  }

  if (currentActiveGate != "") delay(400);

  if (action == "LEFT") {
    fertileWrite(GATE_OPEN_LEFT);
    Serial.println("[GATE] Opening fertile -> " + String(GATE_OPEN_LEFT));
  }
  if (action == "RIGHT") {
    infertileWrite(GATE_OPEN);
    Serial.println("[GATE] Opening infertile -> " + String(GATE_OPEN));
  }

  currentActiveGate = action;
  delay(400);
}

// ===========================
// BOOT TEST
// ===========================

void bootTest() {
  lcdPrint("  LCD TEST OK  ");
  delay(1000);

  lcdPrint("Holder Servo", "OPEN -> " + String(HOLDER_OPEN));
  holderWrite(HOLDER_OPEN);
  delay(500);

  lcdPrint("Holder Servo", "CLOSE -> " + String(HOLDER_CLOSE));
  holderWrite(HOLDER_CLOSE);
  delay(500);

  lcdPrint("Fertile Gate", "OPEN -> " + String(GATE_OPEN_LEFT));
  fertileWrite(GATE_OPEN_LEFT);
  delay(500);

  lcdPrint("Fertile Gate", "CLOSE -> " + String(GATE_IDLE_LEFT));
  fertileWrite(GATE_IDLE_LEFT);
  delay(500);

  lcdPrint("Infertile Gate", "OPEN -> " + String(GATE_OPEN));
  infertileWrite(GATE_OPEN);
  delay(500);

  lcdPrint("Infertile Gate", "CLOSE -> " + String(GATE_IDLE));
  infertileWrite(GATE_IDLE);
  delay(500);

  lcdPrint("IR Sensor", "Check Serial...");
  Serial.println("[BOOT] IR State: " +
    String(digitalRead(IR_SENSOR_PIN) == LOW ? "TRIGGERED" : "CLEAR"));
  delay(1000);

  lcdPrint(" Boot Test OK! ", "   SIHAY!   ");
  Serial.println("[BOOT] All tests passed.");
  delay(1500);
}

// ===========================
// SORTING
// ===========================

void processEgg(String action) {
  lcdPrint("Sorting Egg...", action == "LEFT" ? "FERTILE" : "INFERTILE");
  Serial.println("[SORT] Processing: " + action);

  setGate(action);

  holderWrite(HOLDER_OPEN);
  Serial.println("[SORT] Holder OPEN, motor continuously running");

  unsigned long timeout = millis() + 5000;
  while (digitalRead(IR_SENSOR_PIN) != LOW) {
    if (millis() > timeout) {
      Serial.println("[SORT] IR timeout — egg may have passed");
      break;
    }
    delay(10);
  }

  delay(300);
  holderWrite(HOLDER_CLOSE);
  Serial.println("[SORT] Holder closed");

  lcdPrint("Done!", action == "LEFT" ? "FERTILE" : "INFERTILE");
  delay(1500);

  lcdPrint("   SIHAY!   ");
}

// ===========================
// ESP-NOW CALLBACK
// ===========================

void onDataRecv(const esp_now_recv_info* info, const uint8_t* incomingData, int len) {
  if (len != sizeof(EggResultMessage)) {
    Serial.printf("[ESP-NOW] Bad packet size: %d (expected %d)\n", len, sizeof(EggResultMessage));
    return;
  }

  memcpy(&lastMsg, incomingData, sizeof(lastMsg));

  // Re-assert motor speed immediately after radio event
  ledcWrite(ENB, MOTOR_SPEED);
  digitalWrite(IN3, HIGH);
  digitalWrite(IN4, LOW);
  Serial.println("[ESP-NOW] Motor speed re-asserted");

  Serial.printf("[ESP-NOW] Status: %s | Action: %s | Confidence: %.2f\n",
                lastMsg.status, lastMsg.action, lastMsg.confidence);

  String action = String(lastMsg.action);
  if      (action == "LEFT")  processEgg("LEFT");
  else if (action == "RIGHT") processEgg("RIGHT");
  else    Serial.println("[ESP-NOW] Unknown action, ignoring.");
}

// ===========================
// SETUP
// ===========================

void setup() {
  Serial.begin(115200);
  delay(1000);

  // --- LCD ---
  Wire.begin(I2C_SDA, I2C_SCL);
  lcd.init();
  lcd.backlight();
  lcdPrint("  Booting...  ");

  // --- IR ---
  pinMode(IR_SENSOR_PIN, INPUT);

  // --- Servos ---
  ESP32PWM::allocateTimer(0);
  ESP32PWM::allocateTimer(1);
  ESP32PWM::allocateTimer(2);
  ESP32PWM::allocateTimer(3);

  fertileServo.setPeriodHertz(50);
  infertileServo.setPeriodHertz(50);
  holderServo.setPeriodHertz(50);

  fertileServo.attach(SERVO_FERTILE, 500, 2400);
  infertileServo.attach(SERVO_INFERTILE, 500, 2400);
  holderServo.attach(SERVO_HOLDER, 500, 2400);

  fertileWrite(GATE_IDLE_LEFT);
  infertileWrite(GATE_IDLE);
  holderWrite(HOLDER_CLOSE);

  // --- Motor ---
  pinMode(IN3, OUTPUT);
  pinMode(IN4, OUTPUT);
  ledcAttach(ENB, PWM_FREQ, PWM_RESOLUTION);
  motorStop();

  // --- Boot Test ---
  bootTest();

  // --- Motor on Boot ---
  lcdPrint("Motor Running", BOOT_MOTOR_DIRECTION ? "FORWARD" : "BACKWARD");
  digitalWrite(IN3, BOOT_MOTOR_DIRECTION ? HIGH : LOW);
  digitalWrite(IN4, BOOT_MOTOR_DIRECTION ? LOW : HIGH);
  ledcWrite(ENB, MOTOR_SPEED);

  // --- WiFi ---
  lcdPrint("Syncing WiFi...");
  WiFi.mode(WIFI_STA);
  WiFi.setSleep(false); // FIX: Stop the ESP-NOW packets from dropping due to sleep mode!
  WiFi.begin(ssid, password);

  Serial.print("[WIFI] Connecting");
  while (WiFi.status() != WL_CONNECTED) {
    delay(500);
    Serial.print(".");
  }
  Serial.println("\n[WIFI] Connected!");
  Serial.print("[WIFI] MAC: ");
  Serial.println(WiFi.macAddress());
  Serial.printf("[WIFI] Channel: %d\n", WiFi.channel()); // DEBUG: Verify Channel

  lcdPrint("WiFi Connected!", WiFi.macAddress());
  delay(2000);

  // --- ESP-NOW ---
  if (esp_now_init() != ESP_OK) {
    Serial.println("[ESP-NOW] Init failed! Halting.");
    lcdPrint("ESP-NOW FAILED", "Check Serial");
    while (true) delay(1000);
  }
  esp_now_register_recv_cb(onDataRecv);
  Serial.println("[ESP-NOW] Ready.");

  lcdPrint(" System Ready! ", "   SIHAY!   ");
  Serial.println("[SETUP] Controller ready.");
}

// ===========================
// LOOP
// ===========================

void loop() {
  delay(10);
}