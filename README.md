# AirPods Ultimate Android

## Versión 0.5.0
- Lectura de batería desde anuncios BLE de Apple.
- Decodificación de niveles y bits de carga adaptada al parser de proximidad de LibrePods.
- Lectura del bit `thisInCase` para el AirPod que origina el anuncio.
- Lectura del bit de tapa del estuche.
- Los estados individuales izquierdo/derecho se mantienen como desconocidos si no se pueden atribuir con certeza.
- Los controles de sonido no se simulan ni se presentan como funcionales.

## Compatibilidad
Objetivo inicial: TECNO Pova 4, Android 12. El escaneo BLE funciona con permisos Bluetooth. Los controles de modo de escucha, detección de oído y ajustes avanzados requieren implementar AACP; algunas funciones de LibrePods requieren root/LSPosed o suplantación de Vendor ID. No están habilitados en esta compilación.

Audio espacial con seguimiento de cabeza, monitor cardíaco, Find My completo y audio bidireccional de alta calidad no están disponibles en LibrePods.

## Atribución y licencia
El algoritmo de interpretación de anuncios de proximidad está adaptado de LibrePods, de Kavish Devar, bajo GNU GPL v3.0. Código original y licencia: https://github.com/kavishdevar/librepods. Esta adaptación se distribuye bajo GPL-3.0.
