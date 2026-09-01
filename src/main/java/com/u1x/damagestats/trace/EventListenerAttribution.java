package com.u1x.damagestats.trace;

import net.minecraftforge.eventbus.ASMEventHandler;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.IEventListener;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.forgespi.language.IModInfo;
import net.minecraftforge.forgespi.language.ModFileScanData;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class EventListenerAttribution {
    private static final Pattern ASM_DESCRIPTION = Pattern.compile(
            "^ASM: (?:class )?([\\w.$]+)(?:@[0-9a-fA-F]+)? ([\\w$<>]+)\\(.*$");
    private static final Map<ListenerKey, Resolution> CACHE = new ConcurrentHashMap<>();
    private static volatile Map<String, String> classOwners;

    private EventListenerAttribution() {
    }

    static Resolution resolve(IEventListener listener, Event event) {
        return CACHE.computeIfAbsent(new ListenerKey(listener, event.getClass()),
                ignored -> resolveUncached(listener, event.getClass()));
    }

    private static Resolution resolveUncached(IEventListener listener, Class<?> eventClass) {
        String description = description(listener);
        Matcher matcher = ASM_DESCRIPTION.matcher(description);
        if (matcher.matches()) {
            return resolution(matcher.group(1), matcher.group(2), AttributionConfidence.EXACT,
                    description, "注解事件监听器类与方法均由 Forge 精确提供");
        }

        Object consumer = capturedConsumer(listener);
        if (consumer != null) {
            Class<?> owner = consumerOwner(consumer);
            if (owner.getName().startsWith("net.minecraftforge.eventbus.EventBus")) owner = consumer.getClass();
            List<String> methods = compatibleMethods(owner, eventClass);
            if (methods.size() == 1) {
                String method = methods.get(0);
                return resolution(owner.getName(), method, AttributionConfidence.INFERRED,
                        description + " -> " + owner.getName() + "#" + method,
                        "动态 Consumer 所属类精确；方法由唯一兼容事件参数推断");
            }
            String method = methods.isEmpty() ? "<dynamic>" : "<" + String.join("|", methods) + ">";
            String note = methods.isEmpty()
                    ? "已解开 Forge Lambda 并确定 Consumer 所属类，但没有唯一兼容方法"
                    : "已解开 Forge Lambda 并确定 Consumer 所属类；可能方法: " + String.join(", ", methods);
            return resolution(owner.getName(), method, AttributionConfidence.INFERRED,
                    description + " -> " + owner.getName(), note);
        }

        String className = listener.getClass().getName().replaceAll("/0x[0-9a-fA-F]+$", "");
        return resolution(className, "invoke", AttributionConfidence.UNKNOWN, description,
                "无法读取动态监听器捕获的 Consumer；仅保留包装类");
    }

    private static Resolution resolution(String className, String method, AttributionConfidence confidence,
            String description, String note) {
        String normalizedClass = className.replaceAll("/0x[0-9a-fA-F]+$", "");
        String modId = owners().getOrDefault(normalizedClass, "unknown");
        ContributionSource source = new ContributionSource(modId, "event_listener",
                normalizedClass + "#" + method, simpleName(normalizedClass) + "#" + method);
        return new Resolution(source, confidence, description, note);
    }

    static Object capturedConsumer(Object listener) {
        for (Field field : listener.getClass().getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) continue;
            try {
                if (!field.trySetAccessible()) continue;
                Object value = field.get(listener);
                if (value instanceof Consumer<?>) return value;
            } catch (RuntimeException | IllegalAccessException ignored) {
                // Continue with other captured fields and then fall back to the wrapper class.
            }
        }
        return null;
    }

    static Class<?> consumerOwner(Object consumer) {
        for (Field field : consumer.getClass().getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) || field.getType().isPrimitive()) continue;
            try {
                if (!field.trySetAccessible()) continue;
                Object value = field.get(consumer);
                if (value == null || value instanceof Class<?> || value.getClass().getName().startsWith("java.")) {
                    continue;
                }
                return value.getClass();
            } catch (RuntimeException | IllegalAccessException ignored) {
                // Stateless method references have no receiver and use the lambda nest host below.
            }
        }
        return consumer.getClass().getNestHost();
    }

    static List<String> compatibleMethods(Class<?> owner, Class<?> eventClass) {
        List<String> result = new ArrayList<>();
        for (Method method : owner.getDeclaredMethods()) {
            if (method.isSynthetic() || method.isBridge() || method.getParameterCount() != 1) continue;
            if (!method.getParameterTypes()[0].isAssignableFrom(eventClass)) continue;
            result.add(method.getName());
        }
        return result.stream().distinct().sorted().toList();
    }

    static String description(IEventListener listener) {
        return listener instanceof ASMEventHandler ? listener.toString() : listener.getClass().getName();
    }

    private static Map<String, String> owners() {
        Map<String, String> result = classOwners;
        if (result != null) return result;
        synchronized (EventListenerAttribution.class) {
            if (classOwners != null) return classOwners;
            Map<String, String> built = new HashMap<>();
            for (ModFileScanData scan : ModList.get().getAllScanData()) {
                List<String> modIds = scan.getIModInfoData().stream().flatMap(file -> file.getMods().stream())
                        .map(IModInfo::getModId).distinct().toList();
                for (ModFileScanData.ClassData data : scan.getClasses()) {
                    String className = data.clazz().getClassName();
                    String owner = ownerForClass(className, modIds);
                    if (owner != null) built.putIfAbsent(className, owner);
                }
            }
            classOwners = Map.copyOf(built);
            return classOwners;
        }
    }

    private static String ownerForClass(String className, List<String> modIds) {
        if (modIds.size() == 1) return modIds.get(0);
        String best = null;
        int bestLength = -1;
        for (String modId : modIds) {
            Object mod = ModList.get().getModObjectById(modId).orElse(null);
            if (mod == null) continue;
            String packageName = mod.getClass().getPackageName();
            if (className.startsWith(packageName + ".") && packageName.length() > bestLength) {
                best = modId;
                bestLength = packageName.length();
            }
        }
        return best;
    }

    private static String simpleName(String className) {
        int split = Math.max(className.lastIndexOf('.'), className.lastIndexOf('$'));
        return split >= 0 ? className.substring(split + 1) : className;
    }

    record Resolution(ContributionSource source, AttributionConfidence confidence,
            String description, String note) {
    }

    private record ListenerKey(IEventListener listener, Class<?> eventClass) {
    }
}
