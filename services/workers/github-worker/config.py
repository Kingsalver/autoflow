from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    github_token: str = ""
    github_repo: str = "SimplifyJobs/New-Grad-Positions"

    db_host: str = "localhost"
    db_port: int = 5432
    db_name: str = "autoflow"
    db_user: str = "autoflow_user"
    db_password: str = "strong_password"

    redis_host: str = "localhost"
    redis_port: int = 6379

    kafka_bootstrap_servers: str = "localhost:9092"

    poll_interval_minutes: int = 30
    port: int = 8083

    class Config:
        env_file = ".env"
        env_file_encoding = "utf-8"


settings = Settings()
