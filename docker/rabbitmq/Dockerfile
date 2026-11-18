FROM rabbitmq:3.13-management-alpine

RUN apk add --no-cache curl

RUN curl -fsSL -o /opt/rabbitmq/plugins/rabbitmq_delayed_message_exchange-3.13.0.ez \
    https://github.com/rabbitmq/rabbitmq-delayed-message-exchange/releases/download/v3.13.0/rabbitmq_delayed_message_exchange-3.13.0.ez

RUN rabbitmq-plugins enable --offline rabbitmq_delayed_message_exchange

EXPOSE 5672 15672
