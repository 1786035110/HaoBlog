import { spawnSync } from 'node:child_process'

const args = process.argv.slice(2)
let result = spawnSync('docker', ['compose', 'version'], { stdio: 'ignore' })
const command = result.status === 0 ? ['docker', ['compose', ...args]] : ['docker-compose', args]
result = spawnSync(command[0], command[1], { stdio: 'inherit' })
process.exit(result.status ?? 1)
