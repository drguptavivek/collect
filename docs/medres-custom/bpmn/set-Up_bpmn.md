Here is the **clean, minimal, command-only summary** for the **macOS Node 24 + Puppeteer + bpmn-to-image setup** you completed.

---

### 1️⃣ Install Node 24 via Homebrew

```bash
brew install node@24
```

---

### 2️⃣ Remove old Node links (if any) and link Node 24

```bash
brew unlink node || true
brew unlink node@20 || true
brew link --overwrite --force node@24
```

---

### 3️⃣ Fix Homebrew npm permission conflict (one-time)

```bash
sudo rm -rf /opt/homebrew/lib/node_modules/npm
brew link --overwrite --force node@24
```

---

### 4️⃣ Verify Node & npm

```bash
node -v
npm -v
which node
which npm
```

---

### 5️⃣ Move npm global installs to user space

```bash
npm config set prefix ~/.npm-global
mkdir -p ~/.npm-global
```

---

### 6️⃣ Persist npm global PATH

```bash
echo 'export PATH="$HOME/.npm-global/bin:$PATH"' >> ~/.zshrc
export PATH="$HOME/.npm-global/bin:$PATH"
```

---

### 7️⃣ Install required CLI tools

```bash
npm install -g puppeteer
npm install -g bpmn-to-image
```

---

### 8️⃣ Verify bpmn-to-image

```bash
which bpmn-to-image
bpmn-to-image --help
```

---

### 9️⃣ (Optional) Remove old Node 20

```bash
brew uninstall node@20
```

---

## ✅ Final State

* Node **24.x Active LTS**
* npm globals in `~/.npm-global`
* No `nvm`
* No `sudo npm`
* Homebrew-safe
* EDR-safe
* Puppeteer + bpmn-to-image working
