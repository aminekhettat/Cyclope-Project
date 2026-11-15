/*
void setup() {
  Serial.begin(115200);
  delay(1000);

  Serial.println("DFR1154 : connexion reussie !");
}
*/
/*
void loop() {
  Serial.println("La camera fonctionne !");
  delay(2000);
}
*/
#include "esp_camera.h"
#include <WiFi.h>
#include <WebServer.h>

//brochage DFR1154
#define PWDN_GPIO_NUM   -1
#define RESET_GPIO_NUM  -1
#define XCLK_GPIO_NUM    5

#define Y9_GPIO_NUM      4
#define Y8_GPIO_NUM      6
#define Y7_GPIO_NUM      7
#define Y6_GPIO_NUM     14
#define Y5_GPIO_NUM     17
#define Y4_GPIO_NUM     21
#define Y3_GPIO_NUM     18
#define Y2_GPIO_NUM     16

#define VSYNC_GPIO_NUM   1
#define HREF_GPIO_NUM    2
#define PCLK_GPIO_NUM   15

#define SIOD_GPIO_NUM    8
#define SIOC_GPIO_NUM    9

//wifi (pour le test c mon telephone...)
//nom du reseau
const char* ssid = "The's Galaxy A52s 5G";//The's Galaxy A52s 5G
//mot de passe
const char* password = "sbmw5997";//sbmw5997
//http://192.168.247.197/
//http://192.168.247.197/capture

//serveur HTTP
WebServer server(80); //80 pour http...

bool initCamera() {
  camera_config_t config = {};

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
  config.pixel_format = PIXFORMAT_JPEG;

  config.frame_size = FRAMESIZE_QVGA;
  config.jpeg_quality = 12;
  config.fb_count = 1;
  config.fb_location = CAMERA_FB_IN_PSRAM;
  config.grab_mode = CAMERA_GRAB_WHEN_EMPTY;

  return esp_camera_init(&config) == ESP_OK;
}

//c est pour la page d accueil
void handleRoot() {
  server.send(200, "text/plain","serveur HTTP DFR1154 ok");//200 est la reponse HTTP pour dire que la requete a reussie
}

//capture et envo image JPEG
void handleCapture() {
  camera_fb_t *fb = esp_camera_fb_get();

  if (!fb) {
    server.send(500, "text/plain", "erreur capture");//500 pour erreur internbe serveur
    return;
  }

  WiFiClient client = server.client();

  client.println("HTTP/1.1 200 OK");
  client.println("Content-Type: image/jpeg");
  client.printf("Content-Length: %u\r\n", fb->len);
  client.println("Connection: close");
  client.println();

  client.write(fb->buf, fb->len);

  esp_camera_fb_return(fb);
  client.stop();

  Serial.println("image envoyee");
}

void setup() {
  Serial.begin(115200);

  Serial.println("init camera...");

  if (!initCamera()) {
    Serial.println("rrreur camera");
    return;
  }

  Serial.println("camera initialisee");

  WiFi.begin(ssid, password);

  Serial.print("connexion wifi");

  while (WiFi.status() != WL_CONNECTED) {
    delay(500);
    Serial.print(".");
  }

  Serial.println("\nwifi connecte");
  Serial.print("adresse ip:");
  Serial.println(WiFi.localIP());

  server.on("/", HTTP_GET, handleRoot);
  server.on("/capture", HTTP_GET, handleCapture);

  server.begin();

  Serial.println("serveur http demarre");
}

void loop() {
  server.handleClient();
}