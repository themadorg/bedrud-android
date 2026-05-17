package main

import "bedrud/internal/cli"

var version = "dev"

func main() {
	cli.Execute(version)
}
