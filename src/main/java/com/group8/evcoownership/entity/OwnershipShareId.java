package com.group8.evcoownership.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;

import java.io.Serializable;

@Embeddable
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class OwnershipShareId implements Serializable {

    @Column(name = "UserId", nullable = false)
    private Long userId;

    @Column(name = "GroupId", nullable = false)
    private Long groupId;
}
