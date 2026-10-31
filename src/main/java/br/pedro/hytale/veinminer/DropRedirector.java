package br.pedro.hytale.veinminer;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.Resource;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.system.EcsEvent;
import com.hypixel.hytale.component.event.EntityEventType;
import com.hypixel.hytale.component.event.EntityHolderEventType;
import com.hypixel.hytale.component.event.WorldEventType;
import org.joml.Vector3d;

/** Redirects newly generated drops before insertion; never searches or moves existing world items. */
final class DropRedirector implements ComponentAccessor<EntityStore> {
    private final ComponentAccessor<EntityStore> delegate;
    private final Vector3d origin;
    private final ComponentType<EntityStore, ItemComponent> itemType;
    private final ComponentType<EntityStore, TransformComponent> transformType;

    DropRedirector(ComponentAccessor<EntityStore> delegate, VeinTraversal.Position origin) {
        this(delegate, origin, ItemComponent.getComponentType(), TransformComponent.getComponentType());
    }

    DropRedirector(ComponentAccessor<EntityStore> delegate, VeinTraversal.Position origin,
                   ComponentType<EntityStore, ItemComponent> itemType,
                   ComponentType<EntityStore, TransformComponent> transformType) {
        this.itemType = itemType;
        this.transformType = transformType;
        this.delegate = delegate;
        this.origin = new Vector3d(origin.x() + 0.5, origin.y() + 0.5, origin.z() + 0.5);
    }

    private void redirect(Holder<EntityStore> holder) {
        if (holder.getComponent(itemType) == null) return;
        var transform = holder.getComponent(transformType);
        if (transform != null) transform.setPosition(origin);
    }

    @Override public <T extends Component<EntityStore>> T getComponent(Ref<EntityStore> p0, ComponentType<EntityStore, T> p1) {
        return delegate.getComponent(p0, p1);
    }
    @Override public <T extends Component<EntityStore>> T ensureAndGetComponent(Ref<EntityStore> p0, ComponentType<EntityStore, T> p1) {
        return delegate.ensureAndGetComponent(p0, p1);
    }
    @Override public Archetype<EntityStore> getArchetype(Ref<EntityStore> p0) {
        return delegate.getArchetype(p0);
    }
    @Override public <T extends Resource<EntityStore>> T getResource(ResourceType<EntityStore, T> p0) {
        return delegate.getResource(p0);
    }
    @Override public EntityStore getExternalData() {
        return delegate.getExternalData();
    }
    @Override public <T extends Component<EntityStore>> void putComponent(Ref<EntityStore> p0, ComponentType<EntityStore, T> p1, T p2) {
        delegate.putComponent(p0, p1, p2);
    }
    @Override public <T extends Component<EntityStore>> void addComponent(Ref<EntityStore> p0, ComponentType<EntityStore, T> p1, T p2) {
        delegate.addComponent(p0, p1, p2);
    }
    @Override public <T extends Component<EntityStore>> T addComponent(Ref<EntityStore> p0, ComponentType<EntityStore, T> p1) {
        return delegate.addComponent(p0, p1);
    }
    @Override public Ref<EntityStore>[] addEntities(Holder<EntityStore>[] p0, AddReason p1) {
        for (var holder : p0) redirect(holder);
        return delegate.addEntities(p0, p1);
    }
    @Override public Ref<EntityStore> addEntity(Holder<EntityStore> p0, AddReason p1) {
        redirect(p0);
        return delegate.addEntity(p0, p1);
    }
    @Override public Holder<EntityStore> removeEntity(Ref<EntityStore> p0, Holder<EntityStore> p1, RemoveReason p2) {
        return delegate.removeEntity(p0, p1, p2);
    }
    @Override public <T extends Component<EntityStore>> void removeComponent(Ref<EntityStore> p0, ComponentType<EntityStore, T> p1) {
        delegate.removeComponent(p0, p1);
    }
    @Override public <T extends Component<EntityStore>> void tryRemoveComponent(Ref<EntityStore> p0, ComponentType<EntityStore, T> p1) {
        delegate.tryRemoveComponent(p0, p1);
    }
    @Override public <Event extends EcsEvent> void invoke(Ref<EntityStore> p0, Event p1) {
        delegate.invoke(p0, p1);
    }
    @Override public <Event extends EcsEvent> void invoke(EntityEventType<EntityStore, Event> p0, Ref<EntityStore> p1, Event p2) {
        delegate.invoke(p0, p1, p2);
    }
    @Override public <Event extends EcsEvent> void invoke(Holder<EntityStore> p0, Event p1) {
        delegate.invoke(p0, p1);
    }
    @Override public <Event extends EcsEvent> void invoke(EntityHolderEventType<EntityStore, Event> p0, Holder<EntityStore> p1, Event p2) {
        delegate.invoke(p0, p1, p2);
    }
    @Override public <Event extends EcsEvent> void invoke(Event p0) {
        delegate.invoke(p0);
    }
    @Override public <Event extends EcsEvent> void invoke(WorldEventType<EntityStore, Event> p0, Event p1) {
        delegate.invoke(p0, p1);
    }
}
