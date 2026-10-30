# Word Lens

Boi/khata er pager chobi tulle (ba gallery theke nile) app ta chobir shob English word list kore dey, protita word er Bangla ortho ar synonym shoho.

## APK banano (computer e kichu install korte hobe na)

1. github.com e ekta notun repository banan (Private rakhte paren).
2. Ei folder er shob file (`.github` folder shoho) repository te upload korun.
   - Browser e "Add file > Upload files" diye `.github` folder shoho shob drag-drop korun.
3. Repository r **Actions** tab e jan. "Build APK" workflow automatic cholbe (5-10 minute).
4. Kaj shesh hole oi run e click kore niche **Artifacts** theke `WordLens-apk` download korun.
5. Zip khule `app-debug.apk` phone e pathan, install korun
   (Settings e "Install unknown apps" allow korte hote pare).

## Kibhabe kaj kore

- Chobi theke lekha porha: Google ML Kit (phone er bhitorei, internet lage na).
- Bangla ortho ar synonym: online (internet lagbe), Google Translate public endpoint
  ar fallback hishebe dictionaryapi.dev.

## Sima

- Sudhu English lekha porhe. Hater lekha or jhapsha chobi te bhul hote pare.
- Protita word alada lookup hoy, tai boro pate kichu shomoy lage.
- Bangla ortho word er shadharon ortho, bakyo er context dhore noy.
- Online endpoint gulo unofficial/free, tai kokhono bondho ba slow hote pare.
