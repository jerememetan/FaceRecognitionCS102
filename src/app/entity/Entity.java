package app.entity;

import java.time.LocalDateTime;
import java.util.UUID;

public abstract class Entity {
    private final String id;
    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Entity(){
        this.id = UUID.randomUUID().toString();
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public String getId(){
        return this.id;
    }

    public LocalDateTime getCreatedAt(){
        return this.createdAt;
    }

    public LocalDateTime getUpdatedAt(){
        return this.updatedAt;
    }

    protected void updateTimestamp(){
        this.updatedAt = LocalDateTime.now();
    }
    
}
