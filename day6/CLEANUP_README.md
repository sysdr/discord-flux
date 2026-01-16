# 🧹 Cleanup Script Documentation

## Overview

The `cleanup.sh` script performs comprehensive cleanup of the project, including:
- Stopping all services (Flux Gateway, LoadTest)
- Stopping and removing Docker containers
- Removing development artifacts
- Cleaning cache files
- Checking for API keys and secrets

## Usage

```bash
cd /home/sdr/git/discord-flux/day6
./cleanup.sh
```

## What Gets Cleaned

### 1. Services
- ✅ Stops FluxGateway processes
- ✅ Stops LoadTest processes

### 2. Docker
- ✅ Stops all running containers
- ✅ Removes all stopped containers
- ✅ Prunes unused Docker resources (images, volumes, networks)

### 3. Node.js
- ✅ Removes `node_modules/` directories

### 4. Python
- ✅ Removes `venv/`, `.venv/`, `env/` directories
- ✅ Removes `.pytest_cache/` directories
- ✅ Removes `__pycache__/` directories
- ✅ Removes `*.pyc`, `*.pyo`, `*.pyd` files

### 5. Istio
- ✅ Removes Istio-related files and directories

### 6. Maven
- ✅ Cleans `target/` directories in flux-zombie-reaper

### 7. Logs & Temporary Files
- ✅ Removes `*.log` files
- ✅ Removes temporary files (`.DS_Store`, `*.swp`, etc.)

### 8. Security Check
- ✅ Scans for potential API keys and secrets in code files
- ⚠️ Reports any suspicious files (manual review recommended)

## .gitignore File

The `.gitignore` file is configured to exclude:
- Build artifacts (target/, *.class, *.jar)
- Dependencies (node_modules/, venv/)
- IDE files (.idea/, .vscode/)
- Cache files (__pycache__/, .pytest_cache/)
- Environment files (.env, *.key, *.pem)
- Log files (*.log)
- Istio files (istio/, *istio*)
- Docker override files
- Temporary files

## Safety Notes

1. **The cleanup script does NOT remove:**
   - Source code files
   - Configuration files (unless explicitly specified)
   - Git repository data
   - Project structure

2. **Before running cleanup:**
   - Commit or backup any important changes
   - Stop any running services manually if needed
   - Review the script to ensure it matches your needs

3. **Docker cleanup:**
   - Uses `docker system prune -af --volumes`
   - This removes ALL unused Docker resources
   - Be careful if you have other projects using Docker

## Verification

After running cleanup, verify:
```bash
# Check for any remaining artifacts
find . -name "node_modules" -o -name "venv" -o -name "__pycache__"
find . -name "*.pyc" -o -name ".pytest_cache"

# Check Docker
docker ps -a
docker images

# Check running processes
ps aux | grep -E "FluxGateway|LoadTest"
```

## Manual Cleanup (if needed)

If you need to clean specific items manually:

```bash
# Stop Flux Gateway
pkill -f FluxGateway

# Stop Docker containers
docker stop $(docker ps -q)
docker rm $(docker ps -aq)

# Remove node_modules
find . -name "node_modules" -type d -exec rm -rf {} +

# Remove Python cache
find . -name "__pycache__" -type d -exec rm -rf {} +
find . -name "*.pyc" -delete

# Clean Maven
cd flux-zombie-reaper && mvn clean
```
