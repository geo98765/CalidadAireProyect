package com.calidadaire.core_ingesta;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@Disabled("Necesita PostgreSQL y Mosquitto levantados; se prueba manualmente")
class CoreIngestaApplicationTests {

	@Test
	void contextLoads() {
	}

}