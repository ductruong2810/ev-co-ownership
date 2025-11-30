package com.group8.evcoownership;

import com.group8.evcoownership.testconfig.TestConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestConfig.class)
class EvCoOwnershipApplicationTests {

    @Test
    void contextLoads() {
    }

}
