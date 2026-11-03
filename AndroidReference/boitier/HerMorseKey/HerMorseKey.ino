/*
  HerMorseKey - boîtier USB pour brancher un vrai manipulateur sur HerMorse
  Carte : Arduino Pro Micro (ATmega32U4), ou toute carte 32U4 / RP2040 qui sait faire clavier USB.

  Câblage (prise jack femelle 3,5 mm stéréo) :
    Pointe (tip)    -> broche 2  : point (double palette) ou pioche
    Bague (ring)    -> broche 3  : trait (double palette)
    Corps (sleeve)  -> GND
  Pas de résistance : les entrées utilisent les pull-up internes (appui = contact à la masse).

  Le boîtier se présente comme un clavier USB :
    point / pioche -> Ctrl gauche
    trait          -> Ctrl droit
  HerMorse lit ces deux touches (Réglages > Manipulateur > Source : Externe USB).
*/

#include <Keyboard.h>

const int PIN_DIT = 2;
const int PIN_DAH = 3;
const int LED_RX = 17;          // LED RX du Pro Micro (active à l'état bas)
const unsigned long DEBOUNCE_MS = 4;

bool ditState = false, dahState = false;
unsigned long ditChange = 0, dahChange = 0;

void setup() {
  pinMode(PIN_DIT, INPUT_PULLUP);
  pinMode(PIN_DAH, INPUT_PULLUP);
  pinMode(LED_RX, OUTPUT);
  digitalWrite(LED_RX, HIGH);
  Keyboard.begin();
}

void handle(int pin, bool &state, unsigned long &lastChange, uint8_t key) {
  bool pressed = digitalRead(pin) == LOW;
  unsigned long now = millis();
  if (pressed != state && now - lastChange >= DEBOUNCE_MS) {
    state = pressed;
    lastChange = now;
    if (pressed) Keyboard.press(key); else Keyboard.release(key);
  }
}

void loop() {
  handle(PIN_DIT, ditState, ditChange, KEY_LEFT_CTRL);
  handle(PIN_DAH, dahState, dahChange, KEY_RIGHT_CTRL);
  digitalWrite(LED_RX, (ditState || dahState) ? LOW : HIGH);
}
