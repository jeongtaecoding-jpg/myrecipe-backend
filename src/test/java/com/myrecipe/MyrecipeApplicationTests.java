package com.myrecipe;

import com.myrecipe.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;

// 앱 전체 설정이 뜨는지 확인한다. 개발 DB(.env) 대신 통합 테스트용 MySQL 컨테이너를 쓴다.
class MyrecipeApplicationTests extends IntegrationTestSupport {

	@Test
	void contextLoads() {
	}

}
