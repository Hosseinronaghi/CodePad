from pathlib import Path

root = Path(__file__).resolve().parent
expected = [
    "settings.gradle.kts",
    "build.gradle.kts",
    "gradle/libs.versions.toml",
    "composeApp/build.gradle.kts",
    "composeApp/src/commonMain/kotlin/com/codepad/App.kt",
    "composeApp/src/commonMain/kotlin/com/codepad/Document.kt",
    "composeApp/src/commonMain/kotlin/com/codepad/SearchEngine.kt",
    "composeApp/src/androidMain/kotlin/com/codepad/MainActivity.kt",
    "composeApp/src/androidMain/AndroidManifest.xml",
]
missing = [f for f in expected if not (root / f).is_file()]
if missing:
    raise SystemExit("Missing files: " + ", ".join(missing))
print("Source layout OK")
