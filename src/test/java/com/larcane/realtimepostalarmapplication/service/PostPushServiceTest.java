package com.larcane.realtimepostalarmapplication.service;

import com.larcane.realtimepostalarmapplication.entity.Post;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@SpringBootTest
class PostPushServiceTest {

    @Autowired
    PostPushService postPushService;

    @Test
    @DisplayName("생성 시간이 1시간이 지난 요청이 들어온 경우 처리하지 않아야 함")
    public void test1() {
        // given
        Post post = Post.builder()
                .postId(1L)
                .content("test content")
                .userId(1L)
                .createdAt(LocalDateTime.now().minusHours(2))
                .build();

        // when
        boolean isPushed = postPushService.pushPostCreation(post);

        // then
        assertThat(isPushed).isFalse();
    }

    @Test
    @DisplayName("post content 내에 존재하는 keyword만 반환해야 함")
    public void test2(){
        // given
        String content = "이것은 테스트 컨텐츠입니다. 네이버 주식과 카카오 주식 모두 살 예정입니다.";
        Set<String> keywordSet = new HashSet<>();
        keywordSet.add("카카오");
        keywordSet.add("네이버");
        keywordSet.add("구글");

        // when
        List<String> keywords = postPushService.extractKeyword(content, keywordSet);

        // then
        assertThat(keywords.size()).isEqualTo(2);
        assertThat(keywords.contains("네이버")).isTrue();
        assertThat(keywords.contains("카카오")).isTrue();
    }

    @Test
    @DisplayName("post content 내에 존재하는 keyword가 없는 경우 빈 Set을 반환해야 함")
    public void test3(){
        // given
        String content = "이것은 테스트 컨텐츠입니다. 네이버 주식과 카카오 주식 모두 살 예정입니다.";
        Set<String> keywordSet = new HashSet<>();
        keywordSet.add("구글");

        // when
        List<String> keywords = postPushService.extractKeyword(content, keywordSet);

        // then
        assertThat(keywords.isEmpty()).isTrue();
    }

}