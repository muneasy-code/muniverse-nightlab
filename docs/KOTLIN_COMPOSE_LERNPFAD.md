# MUNIVERSE – Kotlin & Jetpack Compose kennenlernen

Dieses Repository ist unser echtes Android-Lernprojekt. Die Hardwarefunktionen bleiben von der Oberfläche getrennt.

| Datei | Vergleich zur Webentwicklung |
| --- | --- |
| MainActivity.kt | App-Start und Steuerung (app.js) |
| NightLabUi.kt | UI (index.html + style.css) |
| CameraLab.kt | Kamera-Hardwaresteuerung |
| SkySensors.kt | Sensoren und Kompass |
| AstroMath.kt | Sternpositionen berechnen |
| StackLab.kt | Pixelbasierte JPG-Ausrichtung und Mittelung |

## 1. Kotlin gegen JavaScript

JavaScript:

    let iso = 800;
    const appName = "MUNIVERSE";
    function caption(iso) { return "ISO " + iso; }

Kotlin:

    var iso = 800
    val appName = "MUNIVERSE"
    fun caption(iso: Int): String = "ISO " + iso

- val bedeutet unveränderbare Zuweisung (ähnlich const)
- var bedeutet veränderbare Zuweisung (ähnlich let)
- fun definiert eine Funktion
- : Int bezeichnet einen Ganzzahl-Parameter, : String einen Rückgabewert vom Typ Text
- Kommentare beginnen mit // und Semikola sind meist unnötig

## 2. Dein erster Compose-Button

    @Composable
    fun MeinAstroButton() {
        var iso by remember { mutableIntStateOf(800) }

        Column {
            Text("ISO " + iso)
            Button(onClick = { iso = 1600 }) {
                Text("ISO erhöhen")
            }
        }
    }

Das bedeutet:
- @Composable baut ein Stück sichtbarer Oberfläche.
- Column stellt Dinge untereinander dar (wie CSS flex-direction: column).
- Row stellt Dinge nebeneinander dar.
- Modifier.padding(16.dp) ist vergleichbar mit padding in CSS.
- remember + mutableIntStateOf hält einen UI-Wert, der sich ändern kann.
- Compose aktualisiert den Text, sobald der Wert geändert wird.

## 3. Deine erste Änderung direkt auf GitHub (nur mit S23 möglich)

1. Öffne das Repository muneasy-code/muniverse-nightlab und die Datei app/src/main/java/de/muneasy/muniverse/NightLabUi.kt.
2. Suche nach der Zeile mit Text("✦  MUNIVERSE",...) und ändere den Titel in "✦  MUNIVERSE LAB".
3. Speichere die Änderung als Commit auf GitHub.
4. Öffne Actions → Build MUNIVERSE test APK und warte auf einen erfolgreichen Workflow.
5. Lade am erfolgreichen Build das Artefakt MUNIVERSE-debug-APK als ZIP herunter.
6. Entpacke die ZIP und installiere app-debug.apk auf deinem S23.
7. Schau dir die geänderte Überschrift in der App an. Fertig: Dein eigener Code wurde zur installierten Android-App.

Hinweis: GitHub Actions benötigt Internet für die Gradle-Abhängigkeiten. Android Studio brauchst du auf deinem Smartphone nicht.

## 4. Lokal am Computer bauen

1. Android Studio installieren.
2. Get from VCS → https://github.com/muneasy-code/muniverse-nightlab
3. Gradle Sync abwarten.
4. Das Galaxy S23 bei Bedarf über USB anschließen, Entwickleroptionen/USB-Debugging aktivieren.
5. Oben das Gerät auswählen und auf Run ▶ klicken.
6. Eine APK erzeugen: Build → Build Bundle(s) / APK(s) → Build APK(s).

Debug-APK ist nur zum lokalen Testen; für eine reguläre Veröffentlichung benötigt man einen dauerhaften Signierschlüssel.

## 5. Wie wir weiterlernen

Erste Übung: In NightLabUi.kt die Farbkonstante NightCyan ändern und eine neue APK bauen.

Zweite Übung: Missionskarte erweitern; in AstroMath.kt die Sternziel-Liste verstehen.

Dritte Übung: Mit Kotlin eine sinnvolle Bedingung schreiben, die Sterne unter 10° Höhe als derzeit schwer beobachtbar kennzeichnet.

Vierte Übung: Einen selbst programmierten Button ergänzen, ohne Kamera- oder Stack-Code anzufassen.

## Was v0.2 derzeit kann und nicht kann

- Jetpack Compose gestaltet die fünf vorhandenen Appbereiche.
- Die Hardwarelogik Camera2, SkySensors und StackLab bleibt unverändert.
- Das S23-Limit der normalen Camera2-Schnittstelle bleibt 0,15 Sekunden.
- Die Stacking-Funktion berechnet echte Pixel, ersetzt aber kein vollwertiges RAW-Astro-Stacking.
- Vor echtem Einsatz muss der neue Compose-Bildschirm auf dem S23 geprüft werden.
