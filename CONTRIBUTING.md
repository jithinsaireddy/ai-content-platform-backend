# Contributing to AI Content Platform

Thank you for considering contributing to the AI Content Platform! This document outlines the process for contributing to the project and helps ensure a smooth collaboration experience.

## Code of Conduct

By participating in this project, you agree to abide by our [Code of Conduct](CODE_OF_CONDUCT.md).

## How Can I Contribute?

### Reporting Bugs

Before submitting a bug report:
- Check the issue tracker to see if the problem has already been reported
- Collect information about the issue (stack traces, steps to reproduce, etc.)

When submitting a bug report, please include:
- A clear and descriptive title
- Detailed steps to reproduce the issue
- Expected behavior vs. actual behavior
- Any relevant logs or error messages
- Your environment details (OS, Java version, etc.)

### Suggesting Enhancements

Enhancement suggestions are tracked as GitHub issues. When creating an enhancement suggestion, please include:
- A clear and descriptive title
- A detailed description of the proposed functionality
- Any possible implementation approaches
- Why this enhancement would be useful to users

### Pull Requests

1. Fork the repository
2. Create a new branch (`git checkout -b feature/amazing-feature`)
3. Make your changes
4. Run tests to ensure everything works
5. Commit your changes (`git commit -m 'Add amazing feature'`)
6. Push to the branch (`git push origin feature/amazing-feature`)
7. Open a Pull Request

#### Pull Request Guidelines

- Update documentation for any changed functionality
- Keep pull requests focused on a single topic
- Clean up your commit history before submitting
- Follow the project's coding style and conventions
- Include tests that validate your changes

## Development Setup

1. Clone your fork of the repository
2. Install all dependencies: `mvn clean install`
3. Set up environment variables (see `.env.example`)
4. Run with `mvn spring-boot:run`

## Coding Standards

- Follow existing code style
- Write comprehensive Javadoc comments
- Maintain test coverage for your code
- Keep methods focused and reasonably sized

## License

By contributing, you agree that your contributions will be licensed under the project's [Apache License 2.0](LICENSE).

## Questions?

If you have any questions or need help with the contribution process, please open an issue asking for guidance.
