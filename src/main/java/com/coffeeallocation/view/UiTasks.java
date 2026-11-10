package com.coffeeallocation.view;

import java.awt.Component;
import java.awt.Container;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import javax.swing.*;

/** Runs blocking services outside the EDT and serializes actions within one panel. */
public final class UiTasks {
    @FunctionalInterface public interface Work<T> { T call() throws Exception; }
    private static final String BUSY = UiTasks.class.getName() + ".busy";
    private static final AtomicInteger ACTIVE = new AtomicInteger();
    private UiTasks() {}

    public static boolean anyBusy() { return ACTIVE.get() > 0; }
    public static boolean isBusy(JComponent scope) { return Boolean.TRUE.equals(scope.getClientProperty(BUSY)); }

    public static <T> boolean run(JComponent scope, String message, Work<T> work, Consumer<T> success) {
        return run(scope, message, work, success, error -> UiKit.error(scope, error));
    }

    public static <T> boolean run(JComponent scope, String message, Work<T> work,
            Consumer<T> success, Consumer<Throwable> failure) {
        if (!SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("UI tasks must start on the EDT.");
        if (isBusy(scope)) return false;
        scope.putClientProperty(BUSY, true);
        scope.putClientProperty("lastError", null);
        ACTIVE.incrementAndGet();
        Map<Component, Boolean> states = new IdentityHashMap<>();
        disable(scope, states);
        Object status = scope.getClientProperty("statusLabel");
        if (status instanceof JLabel label) label.setText(message);
        new SwingWorker<T, Void>() {
            @Override protected T doInBackground() throws Exception { return work.call(); }
            @Override protected void done() {
                states.forEach(Component::setEnabled);
                scope.putClientProperty(BUSY, false);
                ACTIVE.decrementAndGet();
                try {
                    T result = get();
                    if (status instanceof JLabel label) label.setText("Đã hoàn tất.");
                    success.accept(result);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    failed(exception);
                } catch (ExecutionException exception) {
                    failed(exception.getCause());
                } catch (RuntimeException exception) {
                    failed(exception);
                }
            }
            private void failed(Throwable error) {
                scope.putClientProperty("lastError", error);
                if (status instanceof JLabel label) label.setText("Thao tác chưa hoàn tất. Bạn có thể thử lại.");
                failure.accept(error);
            }
        }.execute();
        return true;
    }

    private static void disable(Component component, Map<Component, Boolean> states) {
        states.put(component, component.isEnabled());
        component.setEnabled(false);
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) disable(child, states);
        }
    }
}
