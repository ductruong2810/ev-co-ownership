package com.group8.evcoownership.repository;

public interface OwnershipGroupRepository {

    boolean existsByGroupName(String groupName);
}
