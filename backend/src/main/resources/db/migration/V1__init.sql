CREATE TABLE orders (
    id                 VARCHAR(32)    PRIMARY KEY,
    customer_email     VARCHAR(255)   NOT NULL,
    customer_name      VARCHAR(255)   NOT NULL,
    status             VARCHAR(20)    NOT NULL,
    total              NUMERIC(12, 2) NOT NULL CHECK (total >= 0),
    refunded_amount    NUMERIC(12, 2) NOT NULL DEFAULT 0 CHECK (refunded_amount >= 0),
    currency           VARCHAR(3)     NOT NULL,
    carrier            VARCHAR(64),
    tracking_number    VARCHAR(64),
    ordered_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    shipped_at         TIMESTAMP WITH TIME ZONE,
    delivered_at       TIMESTAMP WITH TIME ZONE,
    estimated_delivery DATE,
    version            BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT refund_not_above_total CHECK (refunded_amount <= total)
);

CREATE INDEX idx_orders_customer_email ON orders (lower(customer_email));

CREATE TABLE order_items (
    order_id     VARCHAR(32)    NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    position     INTEGER        NOT NULL,
    product_name VARCHAR(255)   NOT NULL,
    quantity     INTEGER        NOT NULL CHECK (quantity > 0),
    unit_price   NUMERIC(12, 2) NOT NULL CHECK (unit_price >= 0),
    PRIMARY KEY (order_id, position)
);

CREATE TABLE refund_requests (
    id              UUID           PRIMARY KEY,
    order_id        VARCHAR(32)    NOT NULL REFERENCES orders (id),
    customer_email  VARCHAR(255)   NOT NULL,
    amount          NUMERIC(12, 2) NOT NULL CHECK (amount > 0),
    reason          VARCHAR(1000)  NOT NULL,
    status          VARCHAR(20)    NOT NULL,
    conversation_id VARCHAR(64),
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    decided_at      TIMESTAMP WITH TIME ZONE,
    decided_by      VARCHAR(100),
    decision_note   VARCHAR(1000),
    version         BIGINT         NOT NULL DEFAULT 0
);

CREATE INDEX idx_refund_requests_status ON refund_requests (status, created_at);
CREATE INDEX idx_refund_requests_customer ON refund_requests (lower(customer_email));
-- At most one pending refund request per order, even under concurrent requests.
CREATE UNIQUE INDEX uq_refund_requests_pending_order ON refund_requests (order_id) WHERE status = 'PENDING';

CREATE TABLE knowledge_sources (
    source       VARCHAR(255) PRIMARY KEY,
    content_hash VARCHAR(64)  NOT NULL,
    chunk_count  INTEGER      NOT NULL,
    loaded_at    TIMESTAMP WITH TIME ZONE NOT NULL
);
