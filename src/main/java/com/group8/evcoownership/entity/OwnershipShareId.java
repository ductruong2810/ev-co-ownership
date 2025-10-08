package com.group8.evcoownership.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.Hibernate;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OwnershipShareId implements Serializable {
    @Serial
    private static final long serialVersionUID = -5841706916567015149L;
    @NotNull
    @Column(name = "UserId", nullable = false)
    private Long userId;

    @NotNull
    @Column(name = "GroupId", nullable = false)
    private Long groupId;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
        OwnershipShareId entity = (OwnershipShareId) o;
        return Objects.equals(this.groupId, entity.groupId) &&
                Objects.equals(this.userId, entity.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(groupId, userId);
    }

}