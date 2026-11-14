APK MAKER (J Studio) - koi coding nahi. Zip ya link daalo, APK milega.

PEHLI BAAR SETUP (sirf ek baar)
1. github.com pe free account banao.
2. New repository banao (naam: apk-maker, Public).
3. "uploading an existing file" pe click karo aur is zip ke ANDAR ka saara content
   (app, gradle, tools, input, .github ... sab) drag-drop karo. Commit karo.
   Agar ".github" folder upload na ho: Add file > Create new file, naam likho
   .github/workflows/build.yml aur "build.yml.COPY-THIS-IF-DOTGITHUB-MISSING.txt" ka text paste karo.

APK BANANA (har baar)
1. Repo me input/ folder kholo. Purani app.zip hatao, apni nayi zip "app.zip" naam se daalo
   (zip ke andar index.html honi chahiye).
   LINK wali app: input/link.txt me sirf website ka link likho (app.zip hata do).
   App ka naam: input/name.txt me likho. Icon: input/icon.png (optional).
2. Commit karo. Repo me Actions tab me build chalega (3-6 minute).
3. Hari tick aane par repo ke right side "Releases" me jao -> app-debug.apk download karo -> phone me install.

NOTE
- Naya APK install karne se pehle purana wala uninstall kar do.
- Voice (mic), copy/paste, file upload app ke andar chalte hain.
- Pehla build is zip me Ghost Translation ke saath ready hai.
