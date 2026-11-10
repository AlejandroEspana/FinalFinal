FROM rabbitmq:3.13-management-alpine

# Instalar curl para descargar el plugin comunitario de delayed exchange
RUN apk add --no-cache curl

# Descargar el plugin oficial rabbitmq_delayed_message_exchange compatible con RabbitMQ 3.13
RUN curl -fsSL -o /opt/rabbitmq/plugins/rabbitmq_delayed_message_exchange-3.13.0.ez \
    https://github.com/rabbitmq/rabbitmq-delayed-message-exchange/releases/download/v3.13.0/rabbitmq_delayed_message_exchange-3.13.0.ez

# Habilitar el plugin en modo offline durante la construcción de la imagen
RUN rabbitmq-plugins enable --offline rabbitmq_delayed_message_exchange

EXPOSE 5672 15672
