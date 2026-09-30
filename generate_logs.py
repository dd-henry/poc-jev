#!/usr/bin/env python3
import json
import random
from pathlib import Path

random.seed(42)

LABELS = {
    "DevOps": {
        "runbook": "RBK-INF-001 - Troubleshooting AKS/K8s",
        "templates": [
            "pod {service} has CrashLoopBackOff in namespace {namespace}",
            "helm upgrade failed for release {service} in {environment}",
            "timeout waiting for condition: pods are not ready in {namespace}",
            "connection reset by peer while hitting {service} service endpoint",
            "kubectl apply failed: error validating data: unknown field {field}",
            "deployment {service} exceeded progress deadline for {environment}",
            "ingress {service} returned 502 and 503 repeatedly from the load balancer",
            "node pressure eviction triggered on {node} during rollout of {service}",
            "aks agentpool {pool} is reporting NotReady and draining nodes",
            "etcd member {member} is unavailable during cluster reconciliation"
        ],
        "fields": ["spec.template.spec.containers[0].image", "metadata.annotations", "serviceAccountName", "resources.limits.cpu"],
        "services": ["payments-api", "checkout-worker", "catalog-service", "user-profile", "ledger-svc", "gateway"],
        "namespaces": ["prod-payments", "dev-platform", "qa-frontend", "observability", "auth-prod", "release-ops"],
        "environments": ["prod", "staging", "pre-prod", "dev", "qa"],
        "nodes": ["aks-nodepool-01", "aks-nodepool-08", "k8s-agent-14", "pool-2-linux"],
        "pools": ["linux-prod", "win-frontend", "gpu-ml", "general-purpose"],
        "members": ["etcd-0", "etcd-1", "etcd-2"]
    },
    "QA": {
        "runbook": "RBK-QA-023 - Falha em Testes E2E",
        "templates": [
            "AssertionError: Expected HTTP 200, but got 503 on /api/v1/payments",
            "E2E test {test_name} failed: element {selector} was not found in DOM",
            "Integration test {test_name} failed because mocked response did not match contract",
            "assertEquals expected {expected} but got {actual} in {feature}",
            "Playwright timeout exceeded waiting for selector {selector} after {seconds}s",
            "test_checkout_flow.py failed on step {step}: user was redirected to /error",
            "database fixture for {feature} was not reset between scenarios",
            "API contract mismatch in {feature}: field {field} missing from response payload",
            "mock server returned 500 during {feature} scenario",
            "selenium session timed out while loading {url}"
        ],
        "tests": ["test_checkout_flow", "test_order_status", "test_login_oauth", "test_inventory_sync", "test_search_results"],
        "selectors": ["[data-testid='place-order']", "#submit-button", "button[aria-label='Confirm']", "[data-test='checkout-step-2']", ".cta-primary"],
        "features": ["checkout", "payments", "identity", "inventory", "search", "notifications"],
        "fields": ["customer_id", "status", "invoice_id", "delivery_eta", "token"],
        "urls": ["https://qa.example.com/checkout", "https://staging.example.com/login", "https://int.example.com/api/v1/orders"],
        "steps": ["tap_submit", "validate_total", "redirect_after_payment", "load_order_details"]
    },
    "Security": {
        "runbook": "RBK-SEC-099 - Revogação de Secrets",
        "templates": [
            "Snyk detected a critical vulnerability in dependency {dependency} used by {service}",
            "secret scan found a hardcoded API key in file {file}",
            "GitLeaks reported a leaked token in commit {commit}",
            "Trivy found HIGH severity CVE in image {image} built by {service}",
            "OWASP Dependency Check flagged {dependency} with known RCE advisory",
            "pipeline log exposes private SSH key in command output for {service}",
            "credential stuffing check identified a revoked secret in {environment}",
            "container image {image} includes vulnerable package {dependency}",
            "npm audit reported critical issue in package {dependency} used by {service}",
            "secret rotation required: Azure Key Vault secret {secret_name} was exposed in logs"
        ],
        "dependencies": ["lodash", "axios", "spring-core", "log4j", "openssl", "requests", "grpc-node", "underscore"],
        "services": ["billing-api", "auth-service", "warehouse-api", "frontend-web", "notifications-job"],
        "files": [".github/workflows/deploy.yml", "Dockerfile", "src/main/resources/application.yml", ".env", "scripts/bootstrap.sh"],
        "commits": ["ab12c4d", "ef88a72", "4d10f3a", "bc9112a", "ff45aa9"],
        "images": ["registry.example.com/payments:1.2.3", "ghcr.io/platform/auth:latest", "docker.io/ops/api:2024.09"],
        "secrets": ["AZURE_CLIENT_SECRET", "DB_PASSWORD", "OPENAI_API_KEY", "GITHUB_TOKEN"]
    },
    "Arquitetura": {
        "runbook": "RBK-ARQ-012 - Refatoração de Injeção de Dependências",
        "templates": [
            "org.springframework.beans.factory.NoSuchBeanDefinitionException: No qualifying bean of type '{bean}' available",
            "UnsatisfiedDependencyException: Error creating bean with name '{bean}' defined in {class}",
            "BeanCreationException: Error creating bean with name '{bean}' nested exception is java.lang.IllegalArgumentException",
            "ApplicationContext failed to start due to duplicate bean definition for {bean}",
            "Circular dependency detected between {bean_a} and {bean_b} in Spring application context",
            "Injection of autowired dependencies failed for {bean}; nested exception is java.lang.NullPointerException",
            "No qualifying bean of type '{bean}' available: expected at least 1 bean which qualifies as autowire candidate",
            "Failed to bind properties under 'app' due to invalid type for field {field}",
            "Constructor injection mismatch for {bean}; required a bean named {dependency} but none was found",
            "Context initialization failed: Bean currently in creation: Is there an unresolvable circular reference?"
        ],
        "beans": ["OrderService", "PaymentClient", "AuditRepository", "EmailSender", "UserGateway", "CatalogClient"],
        "classes": ["com.example.billing.BillingConfig", "com.example.security.AuthConfig", "com.example.checkout.PaymentConfig", "com.example.orders.OrderConfig"],
        "fields": ["app.timeout.ms", "database.pool.max", "cache.ttl", "retry.backoff.ms"],
        "dependencies": ["paymentGatewayClient", "notificationService", "userRepository", "auditService"]
    },
    "SRE": {
        "runbook": "RBK-SRE-045 - Troubleshooting Kafka, Filas e Bancos de Dados",
        "templates": [
            "Kafka consumer group {group} is lagging by {lag} messages on topic {topic}",
            "Kafka broker {broker} is unavailable and partition {partition} is under-replicated",
            "failed to publish event to topic {topic}: NotEnoughReplicasException",
            "queue {queue} has exceeded the oldest message age threshold of {minutes} minutes",
            "dead-letter queue {queue} received {count} messages after repeated processing failures",
            "RabbitMQ channel closed unexpectedly while consuming from queue {queue}",
            "database connection pool exhausted for {database}; timeout waiting for an available connection",
            "PostgreSQL query on {table} exceeded the {seconds}s statement timeout",
            "deadlock detected in database {database} while updating table {table}",
            "Redis cache unavailable; connection refused by {host} on port 6379"
        ],
        "groups": ["payments-consumer", "orders-worker", "notifications-consumer", "fraud-events"],
        "topics": ["payments.events", "orders.created", "inventory.updated", "notifications.send"],
        "brokers": ["kafka-01", "kafka-02", "kafka-03"],
        "partitions": ["0", "3", "7", "12"],
        "queues": ["payments.retry", "orders.dlq", "notifications.pending", "billing.events"],
        "databases": ["payments-db", "orders-db", "customer-db", "analytics-db"],
        "tables": ["orders", "payments", "customer_events", "invoice_items"],
        "hosts": ["cache-prod-01", "redis-primary", "cache-cluster-a"]
    }
}


def build_log(label: str, index: int) -> str:
    meta = LABELS[label]
    template = random.choice(meta["templates"])

    if label == "DevOps":
        service = random.choice(meta["services"])
        namespace = random.choice(meta["namespaces"])
        environment = random.choice(meta["environments"])
        field = random.choice(meta["fields"])
        node = random.choice(meta["nodes"])
        pool = random.choice(meta["pools"])
        member = random.choice(meta["members"])
        replacements = {
            "{service}": service,
            "{namespace}": namespace,
            "{environment}": environment,
            "{field}": field,
            "{node}": node,
            "{pool}": pool,
            "{member}": member,
        }

    elif label == "QA":
        test_name = random.choice(meta["tests"])
        selector = random.choice(meta["selectors"])
        feature = random.choice(meta["features"])
        field = random.choice(meta["fields"])
        url = random.choice(meta["urls"])
        step = random.choice(meta["steps"])
        expected = random.choice(["200", "201", "204", "OK"])
        actual = random.choice(["500", "503", "404", "401"])
        replacements = {
            "{test_name}": test_name,
            "{selector}": selector,
            "{feature}": feature,
            "{field}": field,
            "{url}": url,
            "{step}": step,
            "{expected}": expected,
            "{actual}": actual,
            "{seconds}": str(random.randint(8, 60)),
        }

    elif label == "Security":
        dependency = random.choice(meta["dependencies"])
        service = random.choice(meta["services"])
        file = random.choice(meta["files"])
        commit = random.choice(meta["commits"])
        image = random.choice(meta["images"])
        secret_name = random.choice(meta["secrets"])
        environment = random.choice(["prod", "staging", "qa", "dev"])
        replacements = {
            "{dependency}": dependency,
            "{service}": service,
            "{file}": file,
            "{commit}": commit,
            "{image}": image,
            "{secret_name}": secret_name,
            "{environment}": environment,
        }

    elif label == "Arquitetura":
        bean = random.choice(meta["beans"])
        bean_a = random.choice(meta["beans"])
        bean_b = random.choice(meta["beans"])
        dependency = random.choice(meta["dependencies"])
        field = random.choice(meta["fields"])
        class_name = random.choice(meta["classes"])
        replacements = {
            "{bean}": bean,
            "{bean_a}": bean_a,
            "{bean_b}": bean_b,
            "{dependency}": dependency,
            "{field}": field,
            "{class}": class_name,
        }

    else:
        group = random.choice(meta["groups"])
        topic = random.choice(meta["topics"])
        broker = random.choice(meta["brokers"])
        partition = random.choice(meta["partitions"])
        queue = random.choice(meta["queues"])
        database = random.choice(meta["databases"])
        table = random.choice(meta["tables"])
        host = random.choice(meta["hosts"])
        replacements = {
            "{group}": group,
            "{topic}": topic,
            "{broker}": broker,
            "{partition}": partition,
            "{queue}": queue,
            "{database}": database,
            "{table}": table,
            "{host}": host,
            "{lag}": str(random.randint(100, 50000)),
            "{minutes}": str(random.randint(5, 60)),
            "{count}": str(random.randint(10, 500)),
            "{seconds}": str(random.randint(5, 60)),
        }

    for key, value in replacements.items():
        template = template.replace(key, value)

    return (
        f"[pipeline] build_id={index:03d} source=ci-runner\n"
        f"[error] {template}\n"
        f"[context] release=2024.09.0 environment={random.choice(['prod', 'staging', 'qa'])}\n"
        f"[stack] java.lang.RuntimeException: operation failed\n"
        f"[trace] at org.example.pipeline.Runner.execute(Runner.java:{random.randint(120, 500)})\n"
        f"[trace] at org.example.pipeline.Job.run(Job.java:{random.randint(40, 280)})"
    )


def generate_dataset(count_per_label: int = 25):
    dataset = []
    for label, meta in LABELS.items():
        for i in range(count_per_label):
            dataset.append(build_log(label, i + 1))
    return dataset


def main():
    output_dir = Path("data")
    output_dir.mkdir(exist_ok=True)
    dataset = generate_dataset(25)
    output_path = output_dir / "ci_cd_logs_125.json"
    output_path.write_text(json.dumps(dataset, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"Gerados {len(dataset)} logs em {output_path}")


if __name__ == "__main__":
    main()
