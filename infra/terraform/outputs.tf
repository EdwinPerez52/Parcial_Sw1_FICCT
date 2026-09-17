output "application_url" {
  value = "https://${aws_cloudfront_distribution.main.domain_name}"
}
output "api_load_balancer" {
  value = aws_lb.api.dns_name
}
output "web_bucket" {
  value = aws_s3_bucket.web.id
}
output "artifacts_bucket" {
  value = aws_s3_bucket.artifacts.id
}
output "ecr_repository" {
  value = aws_ecr_repository.api.repository_url
}
output "generation_queue" {
  value = aws_sqs_queue.generation.url
}


