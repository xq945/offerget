package com.offerget.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.deepseek.api.ResponseFormat;
import org.springframework.ai.model.deepseek.autoconfigure.DeepSeekChatProperties;
import org.springframework.ai.model.deepseek.autoconfigure.DeepSeekConnectionProperties;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;

import com.offerget.common.BusinessException;
import com.offerget.config.DeepSeekConfig;

class AiClientTest {

    private ChatClient chatClient;

    private ChatClient.ChatClientRequestSpec requestSpec;

    private ChatClient.CallResponseSpec responseSpec;

    private DeepSeekConnectionProperties properties;

    private DeepSeekChatProperties chatProperties;

    private AiClient aiClient;

    @BeforeEach
    void setUp() {
        chatClient = mock(ChatClient.class);
        requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        responseSpec = mock(ChatClient.CallResponseSpec.class);
        when(chatClient.prompt(any(Prompt.class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);

        properties = new DeepSeekConnectionProperties();
        properties.setApiKey("sk-test");

        chatProperties = new DeepSeekChatProperties();
        chatProperties.setModel("deepseek-flash");

        aiClient = new AiClient(chatClient, mock(RestClient.class), properties, chatProperties);
    }

    @Test
    void missingKeyIsRejectedBeforeCallingModel() {
        properties.setApiKey(DeepSeekConfig.PLACEHOLDER_API_KEY);

        assertThatThrownBy(() -> aiClient.chatJson("system", "user"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException business = (BusinessException) ex;
                    assertThat(business.getCode()).isEqualTo(40000);
                    assertThat(business.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                });

        verifyNoInteractions(chatClient);
    }

    @Test
    void blankKeyIsRejected() {
        properties.setApiKey("  ");

        assertThatThrownBy(() -> aiClient.chatJson("system", "user"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(40000));
    }

    @Test
    void chatJsonMapsMessagesAndJsonOptions() {
        when(responseSpec.chatResponse()).thenReturn(
                new ChatResponse(List.of(new Generation(new AssistantMessage("{\"ok\":true}")))));

        String result = aiClient.chatJson(List.of(
                new AiClient.Message("system", "只输出 JSON"),
                new AiClient.Message("user", "{\"job\":\"后端\"}")), 0.7);

        assertThat(result).isEqualTo("{\"ok\":true}");

        ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
        org.mockito.Mockito.verify(chatClient).prompt(captor.capture());
        Prompt prompt = captor.getValue();

        assertThat(prompt.getInstructions()).hasSize(2);
        assertThat(prompt.getInstructions().get(0)).isInstanceOf(SystemMessage.class);
        assertThat(prompt.getInstructions().get(0).getText()).isEqualTo("只输出 JSON");
        assertThat(prompt.getInstructions().get(1)).isInstanceOf(UserMessage.class);
        assertThat(prompt.getInstructions().get(1).getText()).isEqualTo("{\"job\":\"后端\"}");

        DeepSeekChatOptions options = (DeepSeekChatOptions) prompt.getOptions();
        assertThat(options.getModel()).isEqualTo("deepseek-flash");
        assertThat(options.getTemperature()).isEqualTo(0.7);
        assertThat(options.getMaxTokens()).isEqualTo(8192);
        assertThat(options.getResponseFormat().getType()).isEqualTo(ResponseFormat.Type.JSON_OBJECT);
    }

    @Test
    void temperatureIsClamped() {
        when(responseSpec.chatResponse()).thenReturn(
                new ChatResponse(List.of(new Generation(new AssistantMessage("{}")))));

        aiClient.chatJson(List.of(new AiClient.Message("user", "hi")), 5.0);

        ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
        org.mockito.Mockito.verify(chatClient).prompt(captor.capture());
        assertThat(((DeepSeekChatOptions) captor.getValue().getOptions()).getTemperature())
                .isEqualTo(2.0);
    }

    @Test
    void downstreamFailureBecomesBadGateway() {
        when(responseSpec.chatResponse()).thenThrow(new RuntimeException("connection reset"));

        assertThatThrownBy(() -> aiClient.chatJson("system", "user"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException business = (BusinessException) ex;
                    assertThat(business.getCode()).isEqualTo(50200);
                    assertThat(business.getHttpStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
                });
    }

    @Test
    void emptyModelOutputBecomesBadGateway() {
        when(responseSpec.chatResponse()).thenReturn(
                new ChatResponse(List.of(new Generation(new AssistantMessage("  ")))));

        assertThatThrownBy(() -> aiClient.chatJson("system", "user"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(50200));
    }

    @Test
    void nullResponseBecomesBadGateway() {
        when(responseSpec.chatResponse()).thenReturn(null);

        assertThatThrownBy(() -> aiClient.chatJson("system", "user"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(50200));
    }

}
