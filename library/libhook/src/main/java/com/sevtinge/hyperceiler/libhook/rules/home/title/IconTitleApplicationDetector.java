/*
 * This file is part of HyperCeiler.
 *
 * HyperCeiler is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License.
 */
package com.sevtinge.hyperceiler.libhook.rules.home.title;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

final class IconTitleApplicationDetector {

    private static final String[] APPLICATION_METHODS = {
        "isApplicatoin", // HyperOS 4.50 / legacy MiuiHome spelling
        "isApplication"
    };

    private IconTitleApplicationDetector() {
    }

    static boolean isApplication(Object shortcut) {
        if (shortcut == null) return false;

        for (String methodName : APPLICATION_METHODS) {
            try {
                Method method = shortcut.getClass().getMethod(methodName);
                Object result = method.invoke(shortcut);
                if (result instanceof Boolean value) {
                    return value;
                }
            } catch (NoSuchMethodException ignored) {
                // Try the next known spelling.
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new IllegalStateException(
                    "Unable to query launcher application state via " + methodName,
                    e
                );
            }
        }

        return false;
    }
}
