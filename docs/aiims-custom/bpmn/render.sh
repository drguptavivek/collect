#!/bin/bash
# Render script for BPMN diagrams
# Converts BPMN 2.0 XML files to PNG images using bpmn-to-image

set -e

# Colors for output
GREEN='\033[0;32m'
BLUE='\033[0;34m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# Script directory
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd "$SCRIPT_DIR"

# Create output directory
mkdir -p output

echo -e "${BLUE}Rendering BPMN diagrams...${NC}"
echo ""

# Array of BPMN files
BPMN_FILES=(
    "login-authentication.bpmn"
    "pin-security.bpmn"
    "token-lifecycle.bpmn"
    "data-isolation-logout.bpmn"
    "multiuser-persistence.bpmn"
    "qr-workflow.bpmn"
)

# Render each BPMN file
for bpmn_file in "${BPMN_FILES[@]}"; do
    if [ -f "$bpmn_file" ]; then
        echo -e "${GREEN}Rendering: $bpmn_file${NC}"

        # Run bpmn-to-image (files are created in current directory)
        # Options:
        #   --no-footer: Strip title and logo from image
        #   --scale=1.5: Scale for better quality
        #   --min-dimensions=1200x800: Minimum size

        bpmn-to-image "$bpmn_file:png" \
            --no-footer \
            --scale=1.5 \
            --min-dimensions=1200x800

        if [ $? -eq 0 ]; then
            # Move generated PNG to output directory
            output_name="${bpmn_file%.bpmn}.png"
            mv "$output_name" output/
            echo -e "  ${GREEN}✓ Generated: $output_name${NC}"
        else
            echo -e "  ${RED}✗ Failed: $bpmn_file${NC}"
        fi
    else
        echo -e "${RED}File not found: $bpmn_file${NC}"
    fi
    echo ""
done

echo -e "${GREEN}Done! Images saved to: output/${NC}"
echo ""
echo -e "${BLUE}Generated files:${NC}"
ls -lh output/*.png 2>/dev/null || echo "  No PNG files found"
