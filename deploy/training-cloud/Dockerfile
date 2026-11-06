FROM python:3.12-slim
WORKDIR /app
COPY backend/training_api.py /app/training_api.py
ENV TRAINING_API_HOST=0.0.0.0
ENV TRAINING_API_PORT=8787
EXPOSE 8787
USER nobody
CMD ["python", "/app/training_api.py"]
