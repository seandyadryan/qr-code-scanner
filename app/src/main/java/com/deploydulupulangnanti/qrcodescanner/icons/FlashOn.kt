// Modified 2026 by PT Deploy Dulu Pulang Nanti: package renamed; original vector paths preserved.
/*
 * Copyright 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.deploydulupulangnanti.qrcodescanner.icons

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.ui.graphics.vector.ImageVector

public val Icons.Outlined.FlashOn: ImageVector
    get() {
        if (_flashOn != null) {
            return _flashOn!!
        }
        _flashOn = materialIcon(name = "Outlined.FlashOn") {
            materialPath {
                moveTo(7.0f, 2.0f)
                verticalLineToRelative(11.0f)
                horizontalLineToRelative(3.0f)
                verticalLineToRelative(9.0f)
                lineToRelative(7.0f, -12.0f)
                horizontalLineToRelative(-4.0f)
                lineToRelative(3.0f, -8.0f)
                close()
            }
        }
        return _flashOn!!
    }

private var _flashOn: ImageVector? = null
