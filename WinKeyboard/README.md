# Teclado Win11 para Android

Teclado con el aspecto del teclado en pantalla de Windows 11.

- Emojis con la fuente de Google (Noto Color Emoji, la del sistema), con recientes y categorías
- Modo flotante: se mueve arrastrando la barra de arriba y se redimensiona libremente desde la esquina de abajo a la derecha
- Modo acoplado: arrastra la barra de arriba hacia arriba o abajo para cambiar la altura
- Fondo personalizado (imagen), color de teclas, color de texto, color de fondo
- Modo transparente con nivel ajustable (botón 💧 en la barra)
- Pulsación larga en una tecla: acentos y símbolos extra (á, é, @, |, \, etc.)
- AltGr, Ctrl (Ctrl+C / V / X / A), Mayús, Bloq Mayús, flechas, Esc, Tab, Supr

## Compilar desde el celular (GitHub)

1. Crea un repositorio en GitHub.
2. Sube `WinKeyboard.zip` al repo (Add file > Upload files).
3. Crea el archivo `.github/workflows/build.yml` (Add file > Create new file, escribe esa ruta
   completa como nombre) y pega el contenido del `build.yml` que viene aparte.
4. Entra a la pestaña **Actions**: el workflow descomprime el ZIP dentro del repo, compila y
   publica `WinKeyboard.apk` en **Releases** (y también como artefacto).
5. Descarga el APK, instálalo y abre la app **Teclado Win11** para activarlo.

El ZIP solo se descomprime la primera vez. Para volver a descomprimir (sobrescribiendo), borra la
carpeta `WinKeyboard` del repo o ejecuta el workflow a mano marcando "Volver a descomprimir".
