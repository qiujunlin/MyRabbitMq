package com.javayh.config;

import com.javayh.dao.BrokerMessageLogMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Date;

import static com.javayh.constants.StaticNumber.JAVAYOHO_TOPIC;
import static com.javayh.constants.StaticNumber.SUCCESSFUL_DELIVERY;
import static com.javayh.constants.StaticNumber.TOPIC;
import static com.javayh.constants.StaticNumber.TOPIC_EXCHANGE;
import static com.javayh.constants.StaticNumber.YHJ_TOPIC;

/**
 * @ClassName javayh-rabbitmq → com.javayh.config → TopicRabbitConfig
 * @Description topic配置
 */
@Slf4j
@Configuration
public class TopicRabbitConfig {

    @Autowired
    private CachingConnectionFactory connectionFactory;
    @Autowired
    private BrokerMessageLogMapper brokerMessageLogMapper;

    /**
     * @Description 实例化 rabbitTemplate，进行统一处理
     * @author Dylan
     * @date 2019/9/12
     * @param
     * @return org.springframework.amqp.rabbit.core.RabbitTemplate
     */
    @Bean
    public RabbitTemplate rabbitTemplate() {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(converter());
        // 消息是否成功发送到Exchange
        rabbitTemplate.setConfirmCallback((correlationData, ack, cause) -> {
            if (ack) {
                log.info("消息成功发送到Exchange");
                String msgId = correlationData.getId();
                brokerMessageLogMapper.changeBrokerMessageLogStatus(msgId, SUCCESSFUL_DELIVERY, new Date());
            } else {
                log.info("消息发送到Exchange失败, {}, cause: {}", correlationData, cause);
            }
        });
        // 触发setReturnCallback回调必须设置mandatory=true, 否则Exchange没有找到Queue就会丢弃掉消息, 而不会触发回调
        rabbitTemplate.setMandatory(true);
        // 消息是否从Exchange路由到Queue, 注意: 这是一个失败回调, 只有消息从Exchange路由到Queue失败才会回调这个方法
        rabbitTemplate.setReturnCallback((message, replyCode, replyText, exchange, routingKey) -> {
            log.info("消息从Exchange路由到Queue失败: exchange: {}, route: {}, replyCode: {}, replyText: {}, message: {}", exchange, routingKey, replyCode, replyText, message);
        });

        return rabbitTemplate;
    }

    @Bean
    public Jackson2JsonMessageConverter converter() {
        return new Jackson2JsonMessageConverter();
    }

    /**
     * @Description 创建队列
     * @author Dylan
     * @date 2019/9/5
     * @param
     * @return org.springframework.amqp.core.Queue
     */
    @Bean("queueMessage")
    public Queue queueMessage() {//默认开启
        return new Queue(JAVAYOHO_TOPIC,true);//rounting-key 匹配规则, 是否开启持久化
    }

    /**
     * @Description 创建队列
     * @author Dylan
     * @date 2019/9/5
     * @param
     * @return org.springframework.amqp.core.Queue
     */
    @Bean("queueMessages")
    public Queue queueMessages() {
        return new Queue(YHJ_TOPIC,true);
    }

    /**
     * @Description 创建交换机
     * @UserModule:
     * @author Dylan
     * @date 2019/9/5
     * @param
     * @return org.springframework.amqp.core.Exchange
     */
    @Bean
    Exchange exchange() {
        return ExchangeBuilder.topicExchange(TOPIC_EXCHANGE).durable(true).build();
    }

    /**
     * @Description 绑定交换机
     * @author Dylan
     * @date 2019/9/5
     * @param queueMessage
     * @param exchange
     * @return org.springframework.amqp.core.Binding
     */
    @Bean
    Binding bindingExchangeMessage(Queue queueMessage, TopicExchange exchange) {
        return BindingBuilder.bind(queueMessage).to(exchange).with(JAVAYOHO_TOPIC);
    }

    /**
     * @Description 绑定交换机
     * @author Dylan
     * @date 2019/9/5
     * @param queueMessages
     * @param exchange
     * @return org.springframework.amqp.core.Binding
     */
    @Bean
    Binding bindingExchangeMessages(Queue queueMessages, TopicExchange exchange) {
        return BindingBuilder.bind(queueMessages).to(exchange).with(TOPIC);
    }
}

