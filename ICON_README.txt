Иконка приложения (icon.png)
============================

Куда положить: в папку app/src/main/res/drawable/

Полный путь:
  kael_chat_android\app\src\main\res\drawable\

Что сделать:
  1. Скопируй свой icon.png в эту папку.
  2. Переименуй в:  ic_launcher.png
  3. В AndroidManifest.xml замени:
     android:icon="@drawable/ic_launcher_foreground"
     android:roundIcon="@drawable/ic_launcher_foreground"
     на:
     android:icon="@drawable/ic_launcher"
     android:roundIcon="@drawable/ic_launcher"

Без этого будет использоваться стандартная иконка приложения.
