#!/bin/bash

# Sync script to update GitHub Wiki from docs/aiims-custom
# Usage: ./sync-wiki.sh

WIKI_DIR=".wiki"
DOCS_DIR="docs/aiims-custom"
BRANCH="vg-work"
REPO_URL="https://github.com/drguptavivek/collect"

if [ ! -d "$WIKI_DIR" ]; then
    echo "Error: .wiki directory not found. Is the submodule initialized?"
    exit 1
fi

echo "Cleaning wiki directory..."
find "$WIKI_DIR" -maxdepth 1 -name "*.md" -delete
rm -rf "$WIKI_DIR/assets"

echo "Copying documentation..."
cp README.md "$WIKI_DIR/Home.md"
cp "$DOCS_DIR"/*.md "$WIKI_DIR/"
cp "$DOCS_DIR/ODK_Central_docs"/*.md "$WIKI_DIR/"
mkdir -p "$WIKI_DIR/assets"
cp -r "$DOCS_DIR/assets/"* "$WIKI_DIR/assets/"

echo "Transforming relative code links to absolute GitHub URLs..."
# Update links to aiims-auth-module, collect_app, and open-rosa
# Handles both ../.. and ../ patterns
sed -i '' "s|(\.\./\.\./aiims-auth-module|($REPO_URL/blob/$BRANCH/aiims-auth-module|g" "$WIKI_DIR"/*.md
sed -i '' "s|(\.\./aiims-auth-module|($REPO_URL/blob/$BRANCH/aiims-auth-module|g" "$WIKI_DIR"/*.md
sed -i '' "s|(\.\./\.\./collect_app|($REPO_URL/blob/$BRANCH/collect_app|g" "$WIKI_DIR"/*.md
sed -i '' "s|(\.\./collect_app|($REPO_URL/blob/$BRANCH/collect_app|g" "$WIKI_DIR"/*.md
sed -i '' "s|(\.\./\.\./open-rosa|($REPO_URL/blob/$BRANCH/open-rosa|g" "$WIKI_DIR"/*.md
sed -i '' "s|(\.\./open-rosa|($REPO_URL/blob/$BRANCH/open-rosa|g" "$WIKI_DIR"/*.md

echo "Sync complete. Check '.wiki' directory for changes."

echo "Committing and pushing to GitHub Wiki..."
cd "$WIKI_DIR"
git add .
git commit -m "Sync documentation from docs/aiims-custom"
git push origin master
cd ..

echo "Updating submodule pointer in main repository..."
git add "$WIKI_DIR"
git commit -m "Update wiki submodule pointer"
git push origin "$BRANCH"

echo "Wiki sync and repository update complete!"
