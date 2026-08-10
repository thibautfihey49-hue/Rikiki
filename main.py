from kivy.app import App
from kivy.uix.boxlayout import BoxLayout
from kivy.uix.label import Label
from kivy.uix.button import Button
from kivy.clock import Clock
import os, time, mimetypes, requests, threading
from watchdog.observers import Observer
from watchdog.events import FileSystemEventHandler
from google_auth_oauthlib.flow import InstalledAppFlow
from google.auth.transport.requests import Request
from google.oauth2.credentials import Credentials

SCOPES = ["https://www.googleapis.com/auth/photoslibrary.appendonly"]
DOSSIERS_RACINES = ["/sdcard", "/storage/emulated/0"]
DOSSIERS_IGNORER = {"Android/data", "Android/obb", "Termux", ".thumbnails", "cache", "Cache"}
EXTENSIONS_MEDIA = {".jpg", ".jpeg", ".png", ".gif", ".webp", ".bmp", ".heic", ".heif",
                     ".mp4", ".mov", ".avi", ".mkv", ".flv", ".wmv", ".webm", ".3gp", ".m4v"}

DEJA_UPLOAD = set()
creds_global = None
en_cours = False
statut = "⏳ Déposez credentials.json dans le stockage"

class PhotosSyncApp(App):
    def build(self):
        self.layout = BoxLayout(orientation='vertical', padding=25, spacing=20)
        self.layout.add_widget(Label(text="📸 Sync Google Photos", font_size=26, bold=True, size_hint_y=0.15))
        
        self.label_statut = Label(text=statut, font_size=13, size_hint_y=0.25)
        self.layout.add_widget(self.label_statut)
        
        self.btn_connect = Button(text="🔐 1. Connecter Google", font_size=17, on_press=self.connecter, background_color=(0.2, 0.5, 0.9, 1))
        self.layout.add_widget(self.btn_connect)
        
        self.btn_start = Button(text="▶️ 2. Démarrer la synchro", font_size=17, on_press=self.demarrer, disabled=True, background_color=(0.1, 0.7, 0.3, 1))
        self.layout.add_widget(self.btn_start)
        
        Clock.schedule_interval(self.maj_affichage, 1)
        return self.layout

    def maj_affichage(self, dt):
        self.label_statut.text = statut
        self.btn_start.disabled = creds_global is None

    def connecter(self, instance):
        global creds_global, statut
        token_path = os.path.join(os.environ.get('HOME', '/sdcard'), '.photossync_token.json')
        creds = None
        if os.path.exists(token_path):
            creds = Credentials.from_authorized_user_file(token_path, SCOPES)
        if not creds or not creds.valid:
            if creds and creds.expired and creds.refresh_token:
                creds.refresh(Request())
                creds_global = creds
                statut = "✅ Connecté — Renouvelé automatiquement"
                return
            else:
                creds_file = "/sdcard/credentials.json"
                if not os.path.exists(creds_file):
                    statut = "⚠️ Déposez credentials.json à la racine du stockage"
                    return
                statut = "🔐 Connexion en cours... Ouvrez le lien dans votre navigateur"
                try:
                    flow = InstalledAppFlow.from_client_secrets_file(creds_file, SCOPES)
                    creds = flow.run_local_server(port=8080, open_browser=False)
                    with open(token_path, "w") as f: f.write(creds.to_json())
                    creds_global = creds
                    statut = "✅ Connecté au compte de destination !"
                except Exception as e:
                    statut = f"❌ Erreur : {str(e)[:40]}"
                return
        creds_global = creds
        statut = "✅ Prêt — Cliquez sur Démarrer"

    def demarrer(self, instance):
        global en_cours, statut
        if not en_cours:
            en_cours = True
            threading.Thread(target=self.surveiller_tout, daemon=True).start()
            statut = "🔄 SURVEILLANCE ACTIVE — TOUT le téléphone"
            self.btn_start.text = "⏹️ Arrêter"
            self.btn_start.background_color = (0.8, 0.2, 0.2, 1)
        else:
            en_cours = False
            statut = "⏳ Arrêté"
            self.btn_start.text = "▶️ 2. Démarrer la synchro"
            self.btn_start.background_color = (0.1, 0.7, 0.3, 1)

    def doit_ignorer(self, chemin):
        for ign in DOSSIERS_IGNORER:
            if ign in chemin: return True
        return False

    def est_media(self, chemin):
        return os.path.splitext(chemin.lower())[1] in EXTENSIONS_MEDIA

    def uploader_fichier(self, chemin):
        if chemin in DEJA_UPLOAD or not self.est_media(chemin) or self.doit_ignorer(chemin):
            return
        try:
            with open(chemin, "rb") as f: data = f.read()
            nom = os.path.basename(chemin)
            h = {"Content-Type": "application/octet-stream", "X-Goog-Upload-File-Name": nom}
            rep = requests.post("https://photoslibrary.googleapis.com/v1/uploads", headers=h, data=data, auth=creds_global)
            if rep.status_code != 200: return
            token = rep.text
            corps = {"newMediaItems": [{"simpleMediaItem": {"uploadToken": token}}]}
            requests.post("https://photoslibrary.googleapis.com/v1/mediaItems:batchCreate", json=corps, auth=creds_global)
            DEJA_UPLOAD.add(chemin)
            global statut; statut = f"✅ {nom}"
        except: pass

    def surveiller_tout(self):
        global statut
        statut = "📤 Scan complet du téléphone en cours..."
        for racine in DOSSIERS_RACINES:
            if not os.path.exists(racine): continue
            for dossier, _, fichiers in os.walk(racine):
                if self.doit_ignorer(dossier): continue
                for f in fichiers: self.uploader_fichier(os.path.join(dossier, f))
        
        class Surveilleur(FileSystemEventHandler):
            def __init__(self, app): self.app = app
            def on_created(self, e):
                if not e.is_directory: time.sleep(1); self.app.uploader_fichier(e.src_path)
            def on_modified(self, e):
                if not e.is_directory: time.sleep(1); self.app.uploader_fichier(e.src_path)
        
        obs = Observer()
        for r in DOSSIERS_RACINES:
            if os.path.exists(r): obs.schedule(Surveilleur(self), r, recursive=True)
        obs.start()
        statut = "🔄 Surveillance en temps réel active"
        while en_cours: time.sleep(1)
        obs.stop(); obs.join()

if __name__ == "__main__":
    PhotosSyncApp().run()
