const http = require('http');
const { exec } = require('child_process');

const ADB_PATH = '/home/mondsuchtig/android-sdk/platform-tools/adb';

const server = http.createServer((req, res) => {
  console.log(`[${new Date().toLocaleTimeString()}] ${req.method} ${req.url}`);
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type');

  if (req.method === 'OPTIONS') {
    res.writeHead(204);
    res.end();
    return;
  }

  if (req.url === '/status') {
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ status: 'online' }));
    return;
  }

  if (req.url === '/execute' && req.method === 'POST') {
    let body = '';
    req.on('data', chunk => body += chunk.toString());
    req.on('end', () => {
      try {
        const { action, params } = JSON.parse(body);
        console.log(`Action: ${action}`);

        if (action === 'list_packages') {
          // Получаем отдельно список включенных и выключенных для корректного статуса
          const cmdEnabled = `${ADB_PATH} shell "pm list packages -e"`;
          const cmdDisabled = `${ADB_PATH} shell "pm list packages -d"`;
          
          exec(cmdEnabled, (err1, stdout1) => {
            exec(cmdDisabled, (err2, stdout2) => {
              const enabled = (stdout1 || '').split('\n')
                .filter(line => line.includes('package:'))
                .map(line => ({ name: line.replace('package:', '').trim(), status: 'WORKING' }));
                
              const disabled = (stdout2 || '').split('\n')
                .filter(line => line.includes('package:'))
                .map(line => ({ name: line.replace('package:', '').trim(), status: 'DISABLED' }));
                
              const allPackages = [...enabled, ...disabled].sort((a, b) => a.name.localeCompare(b.name));
              
              console.log(`Returning ${allPackages.length} packages (Enabled: ${enabled.length}, Disabled: ${disabled.length})`);
              res.writeHead(200, { 'Content-Type': 'application/json' });
              res.end(JSON.stringify({ output: allPackages, code: 0 }));
            });
          });
          return;
        }

        let command = '';
        switch (action) {
          case 'freeze': command = `${ADB_PATH} shell "pm disable-user --user 0 ${params.package} || pm disable ${params.package}"`; break;
          case 'unfreeze': command = `${ADB_PATH} shell pm enable ${params.package}`; break;
          case 'force_stop': command = `${ADB_PATH} shell am force-stop ${params.package}`; break;
          case 'uninstall': command = `${ADB_PATH} shell pm uninstall --user 0 ${params.package}`; break;
          case 'dir_size': command = `${ADB_PATH} shell "du -sh '${params.path || '/sdcard/'}' 2>&1"`; break;
          case 'list_dir': command = `${ADB_PATH} shell "ls -lah '${params.path || '/sdcard/'}' 2>&1"`; break;
          case 'shell': command = `${ADB_PATH} shell "${params.command} 2>&1"`; break;
          case 'audit': command = `bash /home/mondsuchtig/.Scripts/full_android_security_audit.sh 2>&1`; break;
          case 'connect_tcp': command = `${ADB_PATH} connect ${params.ip}:${params.port || 5555}`; break;
          case 'disconnect': command = `${ADB_PATH} disconnect`; break;
          case 'tcpip_mode': command = `${ADB_PATH} tcpip ${params.port || 5555}`; break;
          case 'connect_tcp': command = `${ADB_PATH} connect ${params.ip}:${params.port || 5555}`; break;
          case 'disconnect': command = `${ADB_PATH} disconnect`; break;
          case 'tcpip_mode': command = `${ADB_PATH} tcpip ${params.port || 5555}`; break;
          default:
            res.writeHead(400);
            res.end(JSON.stringify({ error: 'Unknown action' }));
            return;
        }

        exec(command, { maxBuffer: 100 * 1024 * 1024 }, (error, stdout, stderr) => {
          const result = (stdout || '') + (stderr || '');
          res.writeHead(200, { 'Content-Type': 'application/json' });
          res.end(JSON.stringify({ output: result.trim(), code: error ? 1 : 0 }));
        });
      } catch (e) {
        console.error(`JSON Parse error: ${e}`);
        res.writeHead(400);
        res.end(JSON.stringify({ error: 'Invalid JSON' }));
      }
    });
    return;
  }

  res.writeHead(404);
  res.end();
});

server.listen(3000, '0.0.0.0', () => {
  console.log('ADB Studio Server v10.2 (Debug Mode) running');
});
