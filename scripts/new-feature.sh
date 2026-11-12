#!/usr/bin/env bash
set -euo pipefail

if [ "$#" -ne 1 ]; then
    echo "Usage: ./scripts/new-feature.sh <feature_name>"
    echo "Example: ./scripts/new-feature.sh school"
    exit 1
fi

FEATURE_NAME="$1"
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FEATURE_DIR="$ROOT_DIR/feature/$FEATURE_NAME"

if [ -d "$FEATURE_DIR" ]; then
    echo "Error: Directory feature/$FEATURE_NAME already exists!"
    exit 1
fi

echo "Scaffolding :feature:$FEATURE_NAME..."

mkdir -p "$FEATURE_DIR/src/main/java/com/github/ljarka/picfort/feature/$FEATURE_NAME"
mkdir -p "$FEATURE_DIR/src/test/java/com/github/ljarka/picfort/feature/$FEATURE_NAME"

# 1. build.gradle.kts
cat << 'EOF' > "$FEATURE_DIR/build.gradle.kts"
plugins {
    id("picfort.android.library")
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.paparazzi)
}

android {
    namespace = "com.github.ljarka.picfort.feature.FEATURE_PLACEHOLDER"

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:ui"))
    implementation(project(":core:database"))
    implementation(project(":core:network"))

    ksp(libs.hilt.android.compiler)

    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.material.icons.core)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.kotlin.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.coil)
    implementation(libs.coil.network.okhttp)

    testImplementation(libs.junit)
    debugImplementation(libs.androidx.ui.tooling)
}
EOF

sed -i '' "s/FEATURE_PLACEHOLDER/$FEATURE_NAME/g" "$FEATURE_DIR/build.gradle.kts"

# 2. AGENTS.md
cat << EOF > "$FEATURE_DIR/AGENTS.md"
# feature:$FEATURE_NAME — AGENTS.md

Vertical slice for $FEATURE_NAME.

## Scope & Files

| File | Purpose |
|---|---|
| \`ui/$FEATURE_NAME/...Screen.kt\` | UI Composables |
| \`ui/$FEATURE_NAME/...ViewModel.kt\` | ViewModel and UI state |
| \`ui/$FEATURE_NAME/...Repository.kt\` | Data source / repository |

## Public Contract / Navigation Entry Points

- Expose entry composable screen to \`:app\` navigation.

## Dependencies & Boundaries

- Depends on \`:core:model\`, \`:core:ui\`, \`:core:database\`, \`:core:network\`.
- **NEVER** depends directly on other feature modules.
- Cross-feature dependencies use interfaces in \`:core:model\`.

## Verification

\`\`\`bash
./gradlew :feature:$FEATURE_NAME:testDebugUnitTest
./gradlew :feature:$FEATURE_NAME:verifyPaparazziDebug
\`\`\`
EOF

# 3. Add to settings.gradle.kts if not present
if ! grep -q "include(\":feature:$FEATURE_NAME\")" "$ROOT_DIR/settings.gradle.kts"; then
    echo "include(\":feature:$FEATURE_NAME\")" >> "$ROOT_DIR/settings.gradle.kts"
fi

# 4. Add to app/build.gradle.kts if not present
if ! grep -q "project(\":feature:$FEATURE_NAME\")" "$ROOT_DIR/app/build.gradle.kts"; then
    sed -i '' "/implementation(project(\":feature:timeline\"))/a\\
    implementation(project(\":feature:$FEATURE_NAME\"))
" "$ROOT_DIR/app/build.gradle.kts"
fi

echo "Created :feature:$FEATURE_NAME successfully."
echo "Run ./scripts/check-modular-boundaries.sh to verify."
