# EliteCast Régie – APK Java Natif

> Application de régie TV et projection Live multi-écrans  
> **100% Java Natif Android** – Sans PeerJS, Sans MQTT  
> Wise Design | Prophète Josias | WhatsApp +240 555 445 514

---

## 🏗️ Architecture

```
EliteCastRegie/
├── app/src/main/java/com/wisedesign/elitecast/
│   ├── EliteCastApp.java          ← Application class + thèmes
│   ├── auth/
│   ├── license/
│   │   └── LicenseManager.java   ← Génération & validation licences
│   ├── network/
│   │   ├── WsServer.java         ← Serveur WebSocket Java (port 9000)
│   │   └── UdpAnnounce.java      ← Broadcast UDP autodiscovery (port 9002)
│   ├── service/
│   │   ├── RegieService.java     ← Foreground service (WS + UDP)
│   │   └── BootReceiver.java
│   └── ui/
│       ├── setup/SplashActivity.java    ← Login / Trial
│       ├── control/RegieActivity.java   ← Régie principale
│       └── settings/
│           ├── AdminActivity.java       ← Génération licences (admin only)
│           └── SettingsActivity.java    ← Thème, langue, déconnexion
```

---

## 🔌 Protocole réseau (compatible Receiver APK)

| Port | Protocole | Rôle |
|------|-----------|------|
| 9000 | WebSocket | Commandes temps réel vers les écrans |
| 9002 | UDP Broadcast | Annonce automatique de la régie |

### Messages JSON (identiques au receiver existant)

```json
// Afficher un verset
{ "type": "project", "text": "Car Dieu a tant aimé...", "ref": "Jean 3:16", "color": "#FFFFFF" }

// Effacer l'écran
{ "type": "clear" }

// Lower-Third (banc-titre)
{ "type": "lt", "name": "Pasteur Jean", "title": "Prédicateur" }

// Fond coloré
{ "type": "bg", "bg": { "color": "#000000" } }
```

---

## 🔑 Système de licences

### Modes d'accès

| Mode | Login | Fonctions |
|------|-------|-----------|
| **Essai gratuit** | Aucun | Max 3 écrans, filigrane TRIAL |
| **Utilisateur Pro** | login + clé ECPRO-... | Fonctions complètes |
| **Admin** | `admin` / `Jesus@_@2026` | Génération de licences |

### Format des clés

```
ECPRO-{6hex}-{6hex}-{90|180|365}-{8hex_checksum}
Exemple : ECPRO-A3F2C1-B8E4D2-365-9A2F1C3E
```

### Générer des licences (admin)

1. Connexion : login `admin`, mot de passe `Jesus@_@2026`
2. L'icône 🛡️ apparaît dans la topbar → ouvre le **Panneau Admin**
3. Sélectionner la durée (90 / 180 / 365 jours)
4. Sélectionner la quantité (1 à 10)
5. Cliquer **⚡ Générer** → copier/partager chaque clé

---

## 🎨 Thèmes disponibles

| Thème | Description |
|-------|-------------|
| ☀️ **Bleu/Blanc** | Bleu profond + blanc (défaut) |
| 🌙 **Sombre** | Noir + violet |
| 🔴 **Bleu/Rouge** | Bleu + accent rouge |

Sélecteur dans : ⚙️ Paramètres ou sur l'écran de connexion.

---

## 🌍 Trilingue

- **FR** Français (défaut)
- **EN** English
- **ES** Español

---

## 🚀 Build GitHub Actions

```bash
# Debug
git push origin main
# → Artifacts : EliteCastRegie-debug-arm64, armv7, universal

# Release
git tag v1.0.0
git push origin v1.0.0
# → GitHub Release avec tous les APKs signés
```

### Secrets GitHub requis pour le release

```
KEYSTORE_BASE64  → keystore JKS encodé en base64
KEY_ALIAS        → alias de la clé
KEY_PASSWORD     → mot de passe de la clé
STORE_PASSWORD   → mot de passe du keystore
```

---

## 📱 Connexion avec le Receiver APK (TV Box)

1. Démarrer **EliteCast Régie** sur le phone/tablette
2. L'annonce UDP démarre automatiquement sur le port 9002
3. Sur la TV Box, lancer le **WscScreen APK** → détection automatique en ~8s
4. L'écran apparaît dans la barre supérieure de la régie

---

## 📞 Contact & Support

**Prophète Josias – Wise Design**  
📱 WhatsApp : +240 555 445 514  
🌍 Bata, Guinée Équatoriale  

---

*EliteCast Régie v1.0 – 2026 – Tous droits réservés Wise Design*
