package com.dangdang.domain.food;

import com.dangdang.domain.food.infra.FoodEmbeddingClient;
import com.dangdang.domain.food.repository.FoodEmbeddingRepository;
import com.dangdang.domain.food.service.FoodEmbeddingFiller;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

// 빈 칸 채우기: DB와 AI 서버는 가짜로 (몇 번 보내고 무엇을 저장하는지만 봄)
class FoodEmbeddingFillerTest {

    final FoodEmbeddingRepository repository = mock(FoodEmbeddingRepository.class);
    final FoodEmbeddingClient client = mock(FoodEmbeddingClient.class);
    final FoodEmbeddingFiller filler = new FoodEmbeddingFiller(repository, client);

    @Test
    void 빈_이름이_없어질_때까지_묶음으로_보내고_밑줄은_띄어쓰기로_바꾼다() {
        float[] a = {1f};
        float[] b = {2f};
        float[] c = {3f};
        given(repository.namesWithoutEmbedding(anyInt()))
                .willReturn(List.of("볶음밥_김치", "달걀찜"), List.of("김밥"), List.of());
        given(client.embed(List.of("볶음밥 김치", "달걀찜"))).willReturn(List.of(a, b));
        given(client.embed(List.of("김밥"))).willReturn(List.of(c));

        int filled = filler.fill();

        assertThat(filled).isEqualTo(3);
        verify(repository).save("볶음밥_김치", a);   // 저장은 원래 이름으로
        verify(repository).save("달걀찜", b);
        verify(repository).save("김밥", c);
    }

    @Test
    void 다_차_있으면_AI_서버를_부르지_않는다() {
        given(repository.namesWithoutEmbedding(anyInt())).willReturn(List.of());

        assertThat(filler.fill()).isZero();
        verify(client, never()).embed(any());
    }

    @Test
    void AI_서버가_실패하면_저장하지_않고_멈춘다() {
        given(repository.namesWithoutEmbedding(anyInt())).willReturn(List.of("김밥"));
        given(client.embed(any())).willThrow(new ResourceAccessException("timeout"));

        assertThat(filler.fill()).isZero();
        verify(client, times(1)).embed(any());
        verify(repository, never()).save(eq("김밥"), any());
    }

    @Test
    void 벡터_수가_다르면_저장하지_않고_멈춘다() {
        given(repository.namesWithoutEmbedding(anyInt())).willReturn(List.of("김밥", "라면"));
        given(client.embed(any())).willReturn(List.of(new float[]{1f}));

        assertThat(filler.fill()).isZero();
        verify(repository, never()).save(any(), any());
    }
}
