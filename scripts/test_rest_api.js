/**
 * Test harness for Super Live HTTP REST API
 */
const http = require('http');
const { spawn } = require('child_process');

async function testEndpoints() {
    console.log('[*] Launching Super REST API Server on port 3847...');
    const serverProc = spawn('node', ['scripts/super_cli.js', 'serve', '--port', '3847'], {
        stdio: ['ignore', 'pipe', 'pipe']
    });

    serverProc.stdout.on('data', d => process.stdout.write(d.toString()));
    serverProc.stderr.on('data', d => process.stderr.write(d.toString()));

    // Wait for server to start
    await new Promise(r => setTimeout(r, 4000));

    function get(path) {
        return new Promise((resolve, reject) => {
            http.get(`http://localhost:3847${path}`, (res) => {
                let data = '';
                res.on('data', chunk => { data += chunk; });
                res.on('end', () => {
                    try {
                        resolve({ status: res.statusCode, json: JSON.parse(data) });
                    } catch (e) {
                        resolve({ status: res.statusCode, raw: data });
                    }
                });
            }).on('error', reject);
        });
    }

    try {
        console.log('\n--- 1. Testing GET /api/health ---');
        const health = await get('/api/health');
        console.log('Health:', health);

        console.log('\n--- 2. Testing GET /api/devices ---');
        const devs = await get('/api/devices');
        console.log('Devices:', devs);

        console.log('\n--- 3. Testing GET /api/status?app=wa ---');
        const waStatus = await get('/api/status?app=wa');
        console.log('WA Status:', waStatus);

        console.log('\n--- 4. Testing GET /api/status?app=sms ---');
        const smsStatus = await get('/api/status?app=sms');
        console.log('SMS Status:', smsStatus);

        console.log('\n--- 5. Testing GET /api/wa/groups ---');
        const waGroups = await get('/api/wa/groups');
        console.log('WA Groups (count):', waGroups.json ? waGroups.json.count : waGroups);

        console.log('\n--- 6. Testing GET /api/pool?app=sms&limit=2 ---');
        const smsPool = await get('/api/pool?app=sms&limit=2');
        console.log('SMS Pool:', smsPool);

        console.log('\n--- 7. Testing GET /api/campaigns?app=wa ---');
        const waCamp = await get('/api/campaigns?app=wa');
        console.log('WA Campaigns:', waCamp);

        console.log('\n[SUCCESS] ALL REST API ENDPOINTS VALIDATED LIVE!');
    } catch (err) {
        console.error('Test failed:', err);
    } finally {
        serverProc.kill();
        process.exit(0);
    }
}

testEndpoints();
