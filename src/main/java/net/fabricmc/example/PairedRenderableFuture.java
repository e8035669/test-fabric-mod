package net.fabricmc.example;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.*;

public class PairedRenderableFuture<V> implements Renderable, Future<V> {

    private ScheduledFuture<V> future;
    private Renderable renderable;

    private boolean canceled;

    public PairedRenderableFuture(ScheduledFuture<V> future, Renderable renderable) {
        this.future = future;
        this.renderable = renderable;
        this.canceled = future.isCancelled();
    }


    @Override
    public void onRendering(WorldRenderContext wrc) {
        if (!this.canceled) {
            this.renderable.onRendering(wrc);
        }
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        this.canceled = true;
        return this.future.cancel(mayInterruptIfRunning);
    }

    @Override
    public boolean isCancelled() {
        return this.future.isCancelled();
    }

    @Override
    public boolean isDone() {
        return this.future.isDone();
    }

    @Override
    public V get() throws InterruptedException, ExecutionException {
        return this.future.get();
    }

    @Override
    public V get(long timeout, @NotNull TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        return this.future.get(timeout, unit);
    }
}
