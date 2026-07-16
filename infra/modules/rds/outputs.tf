output "address" {
  description = "RDS instance endpoint"
  value       = aws_db_instance.main.address
}

output "port" {
  description = "RDS instance port"
  value       = tostring(aws_db_instance.main.port)
}

output "db_name" {
  description = "Name of the database"
  value       = aws_db_instance.main.db_name
}
