#!/usr/bin/env node
/**
 * Super Dynamic Endpoint System & CLI (Node.js)
 * Fully dynamic live endpoint REST API server, interactive REPL, and headless CLI
 * for SuperWA and SuperSMS on all Android devices.
 * 
 * Usage:
 *   # Live HTTP REST API Server
 *   node scripts/super_cli.js serve --port 3000
 * 
 *   # Interactive Live REPL Shell
 *   node scripts/super_cli.js live
 * 
 *   # Headless CLI Commands
 *   node scripts/super_cli.js status --app wa
 *   node scripts/super_cli.js status --app sms
 *   node scripts/super_cli.js diagnostics --app wa
 *   node scripts/super_cli.js send-wa --phone "+919876543210" --message "Hello"
 *   node scripts/super_cli.js send-wa --group "Expenses" --message "Report ready"
 *   node scripts/super_cli.js list-groups
 *   node scripts/super_cli.js send-sms --phone "+919876543210" --message "SMS via Node"
 *   node scripts/super_cli.js call-sms --phone "+919876543210" --duration 5
 *   node scripts/super_cli.js simulate-call --name "Manager" --phone "+919876543210"
 *   node scripts/super_cli.js sync-pool --app sms
 *   node scripts/super_cli.js query-pool --app sms --query "John"
 */

const http = require('http');
const { execSync, spawnSync } = require('child_process');
const fs = require('fs');
const path = require('path');
const readline = require('readline');
const { URL } = require('url');

// ==================== DEVICE MANAGEMENT ====================

function listDevices() {
    try {
        const out = execSync('adb devices -l', { encoding: 'utf8' });
        const lines = out.trim().split('\n');
        const devices = [];
        for (const line of lines) {
            const trimmed = line.trim();
            if (!trimmed || trimmed.startsWith('*') || trimmed.includes('List of devices')) continue;
            const parts = trimmed.split(/\s+/);
            if (parts.length >= 2) {
                const serial = parts[0];
                const state = parts[1];
                let model = '';
                let product = '';
                for (const p of parts.slice(2)) {
                    if (p.startsWith('model:')) model = p.substring(6);
                    if (p.startsWith('product:')) product = p.substring(8);
                }
                devices.push({ serial, state, model: model || product || 'Android' });
            }
        }
        return devices;
    } catch (e) {
        return [];
    }
}

function getTargetDevice(cliDevice) {
    if (cliDevice) return cliDevice;
    if (process.env.ANDROID_SERIAL) return process.env.ANDROID_SERIAL.trim();
    const devices = listDevices();
    const active = devices.filter(d => d.state === 'device');
    if (active.length > 0) {
        return active[0].serial;
    }
    return '10BF1G0HCD0024R';
}

function runAdb(args, device) {
    const fullArgs = ['-s', device, ...args];
    const res = spawnSync('adb', fullArgs, { encoding: 'utf8', maxBuffer: 10 * 1024 * 1024 });
    return {
        stdout: (res.stdout || '').trim(),
        stderr: (res.stderr || '').trim(),
        status: res.status
    };
}

function getPackageForApp(app) {
    return 'com.jmd.superapp';
}

function broadcastCommand(device, pkg, action, extras = []) {
    const receiverClass = `${pkg}/.receiver.TaskDispatchReceiver`;
    const fullAction = action.startsWith('com.jmd.') ? action : `${pkg}.${action}`;
    
    // Safely format extras for Android /bin/sh
    const formattedExtras = extras.map(arg => {
        const str = String(arg);
        if (str.includes(' ') || str.includes('\t') || str.includes('"') || str.includes("'")) {
            return `"${str.replace(/"/g, '\\"')}"`;
        }
        return str;
    });

    const shellCmd = `am broadcast -a ${fullAction} -n ${receiverClass} ${formattedExtras.join(' ')}`;
    const args = ['shell', shellCmd];
    const res = runAdb(args, device);
    
    // Parse result and data from am broadcast output
    let result = 0;
    let data = '';
    const out = res.stdout;
    const resMatch = out.match(/Broadcast completed: result=([-\d]+)/);
    if (resMatch) {
        result = parseInt(resMatch[1], 10);
        const dataPrefix = 'data="';
        const dataIdx = out.indexOf(dataPrefix);
        if (dataIdx !== -1) {
            let extracted = out.substring(dataIdx + dataPrefix.length);
            if (extracted.endsWith('"')) {
                extracted = extracted.slice(0, -1);
            }
            data = extracted;
        }
    } else {
        data = out;
    }
    return { success: result === 1, result, data, raw: out, error: res.stderr };
}

// ==================== CORE ENDPOINT FUNCTIONS ====================

function getStatus(device, app) {
    const pkg = getPackageForApp(app);
    return broadcastCommand(device, pkg, 'ACTION_GET_STATUS');
}

function getDiagnostics(device, app) {
    const pkg = getPackageForApp(app);
    const res = broadcastCommand(device, pkg, 'ACTION_DEVICE_DIAGNOSTICS');
    if (res.data) {
        try {
            res.parsed = JSON.parse(res.data);
        } catch (e) {
            // Keep raw data if not JSON
        }
    }
    return res;
}

function syncPool(device, app) {
    const pkg = getPackageForApp(app);
    return broadcastCommand(device, pkg, 'ACTION_SYNC_POOL');
}

function queryPool(device, app, query = '', limit = 50) {
    const pkg = getPackageForApp(app);
    const extras = ['--ei', 'limit', String(limit)];
    if (query && String(query).trim()) {
        extras.push('--es', 'query', String(query).trim());
    }
    if (app) {
        extras.push('--es', 'app', app);
    }
    const res = broadcastCommand(device, pkg, 'ACTION_QUERY_POOL', extras);
    if (res.data) {
        try {
            res.parsed = JSON.parse(res.data);
        } catch (e) {}
    }
    return res;
}

function sendWaMessage(device, recipient, message, isBusiness = false, isGroup = false) {
    const pkg = 'com.jmd.superapp';
    const extras = [
        '--es', 'message', message,
        '--ez', 'is_business', String(!!isBusiness)
    ];
    if (isGroup || recipient.startsWith('group:')) {
        const cleanGroup = recipient.replace(/^group:/i, '').trim();
        extras.push('--es', 'group', cleanGroup);
    } else {
        extras.push('--es', 'phone', recipient);
    }
    return broadcastCommand(device, pkg, 'ACTION_SEND_WHATSAPP', extras);
}

function callWa(device, phone, duration = 15, isBusiness = false) {
    const pkg = 'com.jmd.superapp';
    const extras = [
        '--es', 'phone', phone,
        '--ez', 'is_business', String(!!isBusiness),
        '--ei', 'duration', String(duration),
        '--ez', 'include_dial_time', 'true'
    ];
    return broadcastCommand(device, pkg, 'ACTION_WHATSAPP_CALL', extras);
}

function postWaStatus(device, message = '', mediaUri = '', app = 'wa') {
    const pkg = 'com.jmd.superapp';
    const waPkg = (app === 'w4b' || app === 'business') ? 'com.whatsapp.w4b' : 'com.whatsapp';
    const extras = ['--es', 'message', message, '--es', 'package', waPkg];
    if (mediaUri) {
        extras.push('--es', 'media_uri', mediaUri, '--ez', 'is_media', 'true');
    }
    return broadcastCommand(device, pkg, 'ACTION_POST_STATUS', extras);
}

function harvestWaGroups(device, isBusiness = false, target = 'GROUPS_ONLY') {
    const pkg = 'com.jmd.superapp';
    const waPkg = isBusiness ? 'com.whatsapp.w4b' : 'com.whatsapp';
    const extras = [
        '--es', 'package', waPkg,
        '--es', 'target', target
    ];
    return broadcastCommand(device, pkg, 'ACTION_HARVEST_WHATSAPP', extras);
}

function dumpWaGroups(device, app = '') {
    const pkg = 'com.jmd.superapp';
    const extras = app ? ['--es', 'app', app] : [];
    return broadcastCommand(device, pkg, 'ACTION_DUMP_GROUPS', extras);
}

function purgeWaGroups(device, all = false) {
    const pkg = 'com.jmd.superapp';
    const extras = all ? ['--ez', 'all', 'true'] : [];
    return broadcastCommand(device, pkg, 'ACTION_PURGE_CORRUPTED_GROUPS', extras);
}

function listWaGroups(device, app = '') {
    const res = dumpWaGroups(device, app);
    if (res.data) {
        try {
            const parsed = JSON.parse(res.data);
            return {
                success: true,
                count: parsed.count !== undefined ? parsed.count : (parsed.groups ? parsed.groups.length : 0),
                groups: parsed.groups || []
            };
        } catch (e) {}
    }
    // Fallback to cat file if available
    const catRes = runAdb(['shell', 'cat', '/sdcard/Download/groups_dump.txt'], device);
    if (catRes.stdout) {
        const lines = catRes.stdout.split('\n').map(l => l.trim()).filter(Boolean);
        const groups = [];
        for (const line of lines) {
            const nameMatch = line.match(/name='(.*?)'/);
            const phoneMatch = line.match(/phone='(.*?)'/);
            const srcMatch = line.match(/src='(.*?)'/);
            if (nameMatch) {
                groups.push({
                    name: nameMatch[1],
                    phone: phoneMatch ? phoneMatch[1] : '',
                    source: srcMatch ? srcMatch[1] : 'WA_GROUP'
                });
            }
        }
        return { success: true, count: groups.length, groups };
    }
    return { success: false, count: 0, groups: [], raw: res.data };
}

function sendSms(device, phone, message, simSlot = 0) {
    const pkg = 'com.jmd.superapp';
    const extras = [
        '--es', 'phone', phone,
        '--es', 'message', message,
        '--ei', 'sim_slot', String(simSlot)
    ];
    return broadcastCommand(device, pkg, 'ACTION_SEND_SMS', extras);
}

function callSms(device, phone, duration = 15, simSlot = 0) {
    const pkg = 'com.jmd.superapp';
    const extras = [
        '--es', 'phone', phone,
        '--ei', 'duration', String(duration),
        '--ei', 'sim_slot', String(simSlot),
        '--ez', 'include_dial_time', 'true'
    ];
    return broadcastCommand(device, pkg, 'ACTION_MAKE_CALL', extras);
}

function simulateCall(device, name, phone, skin = 'PIXEL', timeout = 30) {
    const pkg = 'com.jmd.superapp';
    const extras = [
        '--es', 'caller_name', name || 'Office / Boss',
        '--es', 'caller_number', phone || '+91 98765 43210',
        '--es', 'skin', skin,
        '--ei', 'timeout_seconds', String(timeout)
    ];
    return broadcastCommand(device, pkg, 'ACTION_SIMULATE_CALL', extras);
}

function postNotification(device, title = 'Super Task', content = 'Active task running') {
    const pkg = 'com.jmd.superapp';
    const extras = [
        '--es', 'title', title,
        '--es', 'content', content
    ];
    return broadcastCommand(device, pkg, 'ACTION_POST_NOTIFICATION', extras);
}

function backupDb(device) {
    const pkg = 'com.jmd.superapp';
    return broadcastCommand(device, pkg, 'ACTION_BACKUP_DB');
}

function restoreDb(device) {
    const pkg = 'com.jmd.superapp';
    return broadcastCommand(device, pkg, 'ACTION_RESTORE_DB');
}

function setBankingShield(device, targetPackage, enabled = true) {
    const pkg = 'com.jmd.superapp';
    const extras = [
        '--es', 'package', targetPackage,
        '--ez', 'enabled', String(enabled)
    ];
    return broadcastCommand(device, pkg, 'ACTION_SET_BANKING_SHIELD', extras);
}

function getCampaigns(device, app) {
    const pkg = getPackageForApp(app);
    const res = broadcastCommand(device, pkg, 'ACTION_GET_CAMPAIGNS');
    if (res.data) {
        try {
            res.parsed = JSON.parse(res.data);
        } catch (e) {}
    }
    return res;
}

function triggerCampaignBatch(device, app, campaignId = 1) {
    const pkg = getPackageForApp(app);
    const extras = ['--el', 'campaign_id', String(campaignId)];
    return broadcastCommand(device, pkg, 'ACTION_RUN_CAMPAIGN_BATCH', extras);
}

function getLogs(device, app, lines = 50) {
    const pkg = getPackageForApp(app);
    const res = runAdb(['logcat', '-d', '-s', 'TaskDispatchReceiver:I', 'TaskFeatureApi:I', 'WhatsAppSender:I', 'ContactIngestion:I', 'AppFileLogger:I', '-t', String(lines)], device);
    return {
        success: true,
        device,
        app,
        lines: (res.stdout || '').split('\n').filter(Boolean)
    };
}

// ==================== HTTP REST API SERVER ====================

function startHttpServer(port = 3000, targetDevice = null) {
    const device = getTargetDevice(targetDevice);

    const server = http.createServer(async (req, res) => {
        // Enable CORS
        res.setHeader('Access-Control-Allow-Origin', '*');
        res.setHeader('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');
        res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization');

        if (req.method === 'OPTIONS') {
            res.writeHead(204);
            res.end();
            return;
        }

        const parsedUrl = new URL(req.url, `http://${req.headers.host || 'localhost'}`);
        const pathname = parsedUrl.pathname;
        const q = Object.fromEntries(parsedUrl.searchParams.entries());

        const readBody = () => new Promise((resolve) => {
            let body = '';
            req.on('data', chunk => { body += chunk; });
            req.on('end', () => {
                try {
                    resolve(body ? JSON.parse(body) : {});
                } catch (e) {
                    resolve({});
                }
            });
        });

        const sendJson = (statusCode, payload) => {
            res.writeHead(statusCode, { 'Content-Type': 'application/json; charset=utf-8' });
            res.end(JSON.stringify(payload, null, 2));
        };

        try {
            // GET /api/health
            if (pathname === '/' || pathname === '/api/health') {
                const devs = listDevices();
                return sendJson(200, {
                    status: 'online',
                    version: '2.3.0',
                    activeDevice: device,
                    connectedDevices: devs,
                    timestamp: new Date().toISOString()
                });
            }

            // GET /api/devices
            if (pathname === '/api/devices') {
                return sendJson(200, { success: true, devices: listDevices() });
            }

            // GET /api/status?app=wa|sms
            if (pathname === '/api/status' && req.method === 'GET') {
                const app = (q.app || 'wa').toLowerCase();
                const out = getStatus(device, app);
                return sendJson(200, out);
            }

            // GET /api/diagnostics?app=wa|sms
            if (pathname === '/api/diagnostics' && req.method === 'GET') {
                const app = (q.app || 'wa').toLowerCase();
                const out = getDiagnostics(device, app);
                return sendJson(200, out);
            }

            // GET /api/pool?app=wa|sms&query=...&limit=...
            if (pathname === '/api/pool' && req.method === 'GET') {
                const app = (q.app || 'sms').toLowerCase();
                const out = queryPool(device, app, q.query || '', parseInt(q.limit || '50', 10));
                return sendJson(200, out);
            }

            // POST /api/pool/sync
            if (pathname === '/api/pool/sync' && req.method === 'POST') {
                const body = await readBody();
                const app = (body.app || q.app || 'sms').toLowerCase();
                const out = syncPool(device, app);
                return sendJson(200, out);
            }

            // GET /api/wa/groups
            if (pathname === '/api/wa/groups' && req.method === 'GET') {
                const appFilter = parsedUrl.searchParams.get('app') || parsedUrl.searchParams.get('channel') || '';
                const out = listWaGroups(device, appFilter);
                return sendJson(200, out);
            }

            // POST /api/wa/harvest
            if (pathname === '/api/wa/harvest' && req.method === 'POST') {
                const body = await readBody();
                const out = harvestWaGroups(device, !!body.business, body.target || 'GROUPS_ONLY');
                return sendJson(200, out);
            }

            // POST /api/wa/purge-groups
            if (pathname === '/api/wa/purge-groups' && req.method === 'POST') {
                const body = await readBody();
                const out = purgeWaGroups(device, !!body.all);
                return sendJson(200, out);
            }

            // POST /api/wa/send
            if (pathname === '/api/wa/send' && req.method === 'POST') {
                const body = await readBody();
                const recipient = body.recipient || body.phone || body.group;
                if (!recipient || !body.message) {
                    return sendJson(400, { success: false, error: 'recipient (or phone/group) and message are required' });
                }
                const isGroup = !!body.is_group || !!body.group;
                const out = sendWaMessage(device, recipient, body.message, !!body.is_business, isGroup);
                return sendJson(200, out);
            }

            // POST /api/wa/call
            if (pathname === '/api/wa/call' && req.method === 'POST') {
                const body = await readBody();
                if (!body.phone) {
                    return sendJson(400, { success: false, error: 'phone is required' });
                }
                const out = callWa(device, body.phone, parseInt(body.duration || '15', 10), !!body.is_business);
                return sendJson(200, out);
            }

            // POST /api/wa/status
            if (pathname === '/api/wa/status' && req.method === 'POST') {
                const body = await readBody();
                const out = postWaStatus(device, body.message || '', body.media_uri || '');
                return sendJson(200, out);
            }

            // POST /api/sms/send
            if (pathname === '/api/sms/send' && req.method === 'POST') {
                const body = await readBody();
                if (!body.phone || !body.message) {
                    return sendJson(400, { success: false, error: 'phone and message are required' });
                }
                const out = sendSms(device, body.phone, body.message, parseInt(body.sim_slot || '0', 10));
                return sendJson(200, out);
            }

            // POST /api/sms/call
            if (pathname === '/api/sms/call' && req.method === 'POST') {
                const body = await readBody();
                if (!body.phone) {
                    return sendJson(400, { success: false, error: 'phone is required' });
                }
                const out = callSms(device, body.phone, parseInt(body.duration || '15', 10), parseInt(body.sim_slot || '0', 10));
                return sendJson(200, out);
            }

            // POST /api/sms/simulate-call
            if (pathname === '/api/sms/simulate-call' && req.method === 'POST') {
                const body = await readBody();
                const out = simulateCall(device, body.name, body.phone, body.skin || 'PIXEL', parseInt(body.timeout || '30', 10));
                return sendJson(200, out);
            }

            // POST /api/shield
            if (pathname === '/api/shield' && req.method === 'POST') {
                const body = await readBody();
                if (!body.package) {
                    return sendJson(400, { success: false, error: 'package is required' });
                }
                const out = setBankingShield(device, body.package, body.enabled !== false);
                return sendJson(200, out);
            }

            // GET /api/campaigns
            if (pathname === '/api/campaigns' && req.method === 'GET') {
                const app = (q.app || 'wa').toLowerCase();
                const out = getCampaigns(device, app);
                return sendJson(200, out);
            }

            // POST /api/campaigns/batch
            if (pathname === '/api/campaigns/batch' && req.method === 'POST') {
                const body = await readBody();
                const app = (body.app || q.app || 'wa').toLowerCase();
                const cid = parseInt(body.campaign_id || '1', 10);
                const out = triggerCampaignBatch(device, app, cid);
                return sendJson(200, out);
            }

            // GET /api/logs
            if (pathname === '/api/logs' && req.method === 'GET') {
                const app = (q.app || 'wa').toLowerCase();
                const lines = parseInt(q.lines || '50', 10);
                const out = getLogs(device, app, lines);
                return sendJson(200, out);
            }

            return sendJson(404, { error: 'Endpoint not found', path: pathname });

        } catch (err) {
            return sendJson(500, { error: err.message, stack: err.stack });
        }
    });

    server.listen(port, () => {
        console.log(`
======================================================================
🚀 SUPER LIVE ENDPOINT SERVER RUNNING
======================================================================
  URL:            http://localhost:${port}
  Active Device:  ${device}
  Available APIs:
    - GET  /api/health
    - GET  /api/devices
    - GET  /api/status?app=wa|sms
    - GET  /api/diagnostics?app=wa|sms
    - GET  /api/pool?app=wa|sms&query=...&limit=50
    - POST /api/pool/sync
    - GET  /api/wa/groups
    - POST /api/wa/harvest
    - POST /api/wa/purge-groups
    - POST /api/wa/send
    - POST /api/wa/call
    - POST /api/wa/status
    - POST /api/sms/send
    - POST /api/sms/call
    - POST /api/sms/simulate-call
    - POST /api/shield
    - GET  /api/campaigns?app=wa|sms
    - POST /api/campaigns/batch
    - GET  /api/logs?app=wa|sms&lines=50
======================================================================
Press Ctrl+C to terminate server.
        `);
    });
}

// ==================== INTERACTIVE REPL MODE ====================

function startRepl(targetDevice, initialApp = 'wa') {
    const device = getTargetDevice(targetDevice);
    let app = initialApp;

    console.log(`
======================================================================
📱 SUPER LIVE INTERACTIVE REPL
======================================================================
Active Device: ${device} | Target App: ${app.toUpperCase()}
Type 'help' for commands, 'app <wa|sms>' to switch, 'exit' to quit.
======================================================================
    `);

    const rl = readline.createInterface({
        input: process.stdin,
        output: process.stdout,
        prompt: `super[${app}]> `
    });

    rl.prompt();

    rl.on('line', (line) => {
        const trimmed = line.trim();
        if (!trimmed) {
            rl.prompt();
            return;
        }

        const parts = trimmed.split(/\s+/);
        const cmd = parts[0].toLowerCase();
        const args = parts.slice(1);

        switch (cmd) {
            case 'exit':
            case 'quit':
                rl.close();
                return;

            case 'help':
                console.log(`
Commands:
  app <wa|sms>           Switch active app
  devices                List attached Android devices
  status                 Query live app status
  diag                   Query deep device diagnostics
  sync                   Synchronize contact pool
  pool [query]           Search/inspect dynamic contact pool
  groups                 List harvested WhatsApp groups
  harvest                Trigger accessibility group harvest
  purge                  Purge corrupted group pool entries
  purge-all              Purge ALL group pool entries
  send-wa <phone> <msg>  Send direct WhatsApp message
  group-wa <name> <msg>  Send WhatsApp group message
  call-wa <phone> [dur]  Make WhatsApp voice call
  status-wa <msg>        Post WhatsApp Status
  send-sms <phone> <msg> Send cellular SMS
  call-sms <phone> [dur] Make cellular phone call
  simulate <name> <phone> Simulate incoming call
  shield <pkg> [on|off]  Set banking app shield
  logs [lines]           Tail recent logs
                `);
                break;

            case 'app':
                if (args[0] && (args[0] === 'wa' || args[0] === 'sms')) {
                    app = args[0];
                    console.log(`Switched to: ${app.toUpperCase()}`);
                } else {
                    console.log(`Usage: app <wa|sms>`);
                }
                break;

            case 'devices':
                console.log(listDevices());
                break;

            case 'status':
                console.log(getStatus(device, app).data);
                break;

            case 'diag':
                const diag = getDiagnostics(device, app);
                console.log(diag.parsed ? JSON.stringify(diag.parsed, null, 2) : diag.data);
                break;

            case 'sync':
                console.log(syncPool(device, app).data);
                break;

            case 'pool':
                const poolRes = queryPool(device, app, args.join(' ') || '', 20);
                console.log(poolRes.parsed ? JSON.stringify(poolRes.parsed, null, 2) : poolRes.data);
                break;

            case 'groups':
                console.log(listWaGroups(device, args[0] || ''));
                break;

            case 'harvest':
                console.log(harvestWaGroups(device).data);
                break;

            case 'purge':
                console.log(purgeWaGroups(device, false).data);
                break;

            case 'purge-all':
                console.log(purgeWaGroups(device, true).data);
                break;

            case 'send-wa':
                if (args.length < 2) {
                    console.log('Usage: send-wa <phone> <message>');
                } else {
                    console.log(sendWaMessage(device, args[0], args.slice(1).join(' ')).data);
                }
                break;

            case 'group-wa':
                if (args.length < 2) {
                    console.log('Usage: group-wa <groupName> <message>');
                } else {
                    console.log(sendWaMessage(device, args[0], args.slice(1).join(' '), false, true).data);
                }
                break;

            case 'call-wa':
                if (!args[0]) {
                    console.log('Usage: call-wa <phone> [durationSec]');
                } else {
                    console.log(callWa(device, args[0], parseInt(args[1] || '15', 10)).data);
                }
                break;

            case 'status-wa':
                if (!args[0]) {
                    console.log('Usage: status-wa <message>');
                } else {
                    console.log(postWaStatus(device, args.join(' ')).data);
                }
                break;

            case 'send-sms':
                if (args.length < 2) {
                    console.log('Usage: send-sms <phone> <message>');
                } else {
                    console.log(sendSms(device, args[0], args.slice(1).join(' ')).data);
                }
                break;

            case 'call-sms':
                if (!args[0]) {
                    console.log('Usage: call-sms <phone> [durationSec]');
                } else {
                    console.log(callSms(device, args[0], parseInt(args[1] || '15', 10)).data);
                }
                break;

            case 'simulate':
                console.log(simulateCall(device, args[0] || 'Manager', args[1] || '+919769602020').data);
                break;

            case 'shield':
                if (!args[0]) {
                    console.log('Usage: shield <package> [on|off]');
                } else {
                    const enable = args[1] !== 'off';
                    console.log(setBankingShield(device, args[0], enable).data);
                }
                break;

            case 'logs':
                console.log(getLogs(device, app, parseInt(args[0] || '25', 10)).lines.join('\n'));
                break;

            default:
                console.log(`Unknown command: ${cmd}. Type 'help' for list of commands.`);
        }

        rl.setPrompt(`super[${app}]> `);
        rl.prompt();
    }).on('close', () => {
        console.log('\nExiting Super REPL.');
        process.exit(0);
    });
}

// ==================== CLI PARSER & ENTRYPOINT ====================

function parseArgs() {
    const args = process.argv.slice(2);
    if (args.length === 0) {
        printUsage();
        process.exit(1);
    }
    const command = args[0];
    const flags = {};
    for (let i = 1; i < args.length; i++) {
        if (args[i].startsWith('--')) {
            const key = args[i].substring(2);
            if (i + 1 < args.length && !args[i + 1].startsWith('--')) {
                flags[key] = args[i + 1];
                i++;
            } else {
                flags[key] = true;
            }
        } else if (args[i].startsWith('-')) {
            const key = args[i].substring(1);
            if (i + 1 < args.length && !args[i + 1].startsWith('-')) {
                flags[key] = args[i + 1];
                i++;
            } else {
                flags[key] = true;
            }
        }
    }
    return { command, flags };
}

function printUsage() {
    console.log(`
Super Dynamic Endpoint CLI (Node.js)
Usage:
  node scripts/super_cli.js <command> [options]

Core Modes:
  serve                 Launch live HTTP REST API server (--port 3000)
  live, repl            Launch interactive terminal REPL shell

Commands:
  devices               List attached Android devices
  status                Get service & app status (--app wa|sms)
  diagnostics           Get deep device & app specs in JSON (--app wa|sms)
  pool, query-pool      Query dynamic contact pool (--app wa|sms, [--query text], [--limit 50])
  sync-pool             Sync dynamic contact pool (--app wa|sms)
  send-wa               Send WhatsApp message (--phone or --group, --message, [--business])
  call-wa               Trigger WhatsApp voice call (--phone, [--duration 15], [--business])
  post-status           Post WhatsApp Status (--message, [--media URI])
  harvest-groups        Scan & harvest WhatsApp groups (--business)
  groups, list-groups   List discovered WhatsApp groups from pool
  dump-groups           Dump harvested groups to logcat & disk
  purge-groups          Purge non-group chats from group pool ([--all])
  send-sms              Send cellular SMS (--phone, --message, [--sim 0])
  call-sms              Make cellular call (--phone, [--duration 15], [--sim 0])
  simulate-call         Simulate inbound call (--name, --phone, [--skin PIXEL])
  notify                Post test notification (--title, --content)
  shield                Set banking shield (--package, [--disable])
  campaigns             List active campaigns (--app wa|sms)
  batch                 Trigger campaign outreach batch (--app wa|sms, --campaign-id 1)
  logs                  Stream live recent logs (--app wa|sms, [--lines 50])

Options:
  --device, -d <serial> Target device serial (auto-detected if omitted)
  --app <wa|sms>        Target application (default: wa)
  --port <number>       HTTP server port (default: 3000)
`);
}

function main() {
    const { command, flags } = parseArgs();
    const device = getTargetDevice(flags.device || flags.d);
    const app = (flags.app || 'wa').toLowerCase();

    switch (command) {
        case 'serve': {
            const port = parseInt(flags.port || '3000', 10);
            startHttpServer(port, device);
            break;
        }

        case 'live':
        case 'repl': {
            startRepl(device, app);
            break;
        }

        case 'devices': {
            const devs = listDevices();
            console.log(JSON.stringify({ activeDevice: device, devices: devs }, null, 2));
            break;
        }

        case 'status': {
            console.log(`[*] Querying Status for ${app.toUpperCase()} on ${device}...`);
            const res = getStatus(device, app);
            console.log(res.data);
            break;
        }

        case 'diagnostics': {
            console.log(`[*] Querying Device Diagnostics for ${app.toUpperCase()} on ${device}...`);
            const res = getDiagnostics(device, app);
            console.log(res.parsed ? JSON.stringify(res.parsed, null, 2) : res.data);
            break;
        }

        case 'pool':
        case 'contacts':
        case 'list-contacts':
        case 'query-pool': {
            const q = flags.query || '';
            const limit = parseInt(flags.limit || '50', 10);
            console.log(`[*] Querying Dynamic Contact Pool for ${app.toUpperCase()} (query='${q}', limit=${limit}) on ${device}...`);
            const res = queryPool(device, app, q, limit);
            console.log(res.parsed ? JSON.stringify(res.parsed, null, 2) : res.data);
            break;
        }

        case 'sync-pool': {
            console.log(`[*] Syncing Contact Pool for ${app.toUpperCase()} on ${device}...`);
            const res = syncPool(device, app);
            console.log(res.data);
            break;
        }

        case 'send-wa': {
            const recipient = flags.phone || flags.group;
            if (!recipient || !flags.message) {
                console.error('[Error] --phone (or --group) and --message are required');
                process.exit(1);
            }
            const isGroup = !!flags.group;
            console.log(`[*] Sending WhatsApp to ${recipient} (isGroup=${isGroup}, isBiz=${!!flags.business}) on ${device}...`);
            const res = sendWaMessage(device, recipient, flags.message, !!flags.business, isGroup);
            console.log(res.data);
            break;
        }

        case 'call-wa': {
            if (!flags.phone) {
                console.error('[Error] --phone is required');
                process.exit(1);
            }
            const dur = parseInt(flags.duration || '15', 10);
            console.log(`[*] Calling ${flags.phone} via WhatsApp on ${device}...`);
            const res = callWa(device, flags.phone, dur, !!flags.business);
            console.log(res.data);
            break;
        }

        case 'post-status': {
            const msg = flags.message || '';
            const app = flags.app || flags.channel || 'wa';
            console.log(`[*] Posting WhatsApp Status on ${device} (app=${app})...`);
            const res = postWaStatus(device, msg, flags.media || '', app);
            console.log(res.data);
            break;
        }

        case 'harvest-groups': {
            console.log(`[*] Triggering Group Harvest on ${device}...`);
            const res = harvestWaGroups(device, !!flags.business);
            console.log(res.data);
            break;
        }

        case 'dump-groups': {
            console.log(`[*] Dumping harvested WhatsApp groups on ${device}...`);
            const res = dumpWaGroups(device);
            console.log(res.data);
            break;
        }

        case 'groups':
        case 'list-groups': {
            const appFilter = flags.app || flags.channel || '';
            console.log(`[*] Listing Harvested WhatsApp Groups on ${device} (filter='${appFilter}')...`);
            const res = listWaGroups(device, appFilter);
            console.log(JSON.stringify(res, null, 2));
            break;
        }

        case 'purge-groups': {
            console.log(`[*] Purging non-group entries from pool on ${device} (all=${!!flags.all})...`);
            const res = purgeWaGroups(device, !!flags.all);
            console.log(res.data);
            break;
        }

        case 'send-sms': {
            if (!flags.phone || !flags.message) {
                console.error('[Error] --phone and --message are required');
                process.exit(1);
            }
            const sim = parseInt(flags.sim || '0', 10);
            console.log(`[*] Sending SMS to ${flags.phone} via SIM ${sim} on ${device}...`);
            const res = sendSms(device, flags.phone, flags.message, sim);
            console.log(res.data);
            break;
        }

        case 'call-sms': {
            if (!flags.phone) {
                console.error('[Error] --phone is required');
                process.exit(1);
            }
            const sim = parseInt(flags.sim || '0', 10);
            const dur = parseInt(flags.duration || '15', 10);
            console.log(`[*] Making Phone Call to ${flags.phone} (duration=${dur}s, sim=${sim}) on ${device}...`);
            const res = callSms(device, flags.phone, dur, sim);
            console.log(res.data);
            break;
        }

        case 'simulate-call': {
            const name = flags.name || 'Office / Boss';
            const phone = flags.phone || '+91 98765 43210';
            const skin = flags.skin || 'PIXEL';
            const timeout = parseInt(flags.timeout || '30', 10);
            console.log(`[*] Simulating Inbound Call from ${name} (${phone}) on ${device}...`);
            const res = simulateCall(device, name, phone, skin, timeout);
            console.log(res.data);
            break;
        }

        case 'notify': {
            const title = flags.title || 'Super Task';
            const content = flags.content || 'Active task running';
            console.log(`[*] Posting notification "${title}: ${content}" on ${device}...`);
            const res = postNotification(device, title, content);
            console.log(res.data);
            break;
        }

        case 'backup': {
            console.log(`[*] Backing up Super database on ${device}...`);
            const res = backupDb(device);
            console.log(res.data);
            break;
        }

        case 'restore': {
            console.log(`[*] Restoring Super database on ${device}...`);
            const res = restoreDb(device);
            console.log(res.data);
            break;
        }

        case 'shield': {
            if (!flags.package) {
                console.error('[Error] --package is required');
                process.exit(1);
            }
            const enable = !flags.disable;
            console.log(`[*] Setting Banking Shield for ${flags.package} (${enable ? 'ENABLE' : 'DISABLE'}) on ${device}...`);
            const res = setBankingShield(device, flags.package, enable);
            console.log(res.data);
            break;
        }

        case 'campaigns': {
            console.log(`[*] Querying Campaigns for ${app.toUpperCase()} on ${device}...`);
            const res = getCampaigns(device, app);
            console.log(res.parsed ? JSON.stringify(res.parsed, null, 2) : res.data);
            break;
        }

        case 'batch': {
            const cid = parseInt(flags['campaign-id'] || '1', 10);
            console.log(`[*] Dispatching Campaign Batch (id=${cid}) for ${app.toUpperCase()} on ${device}...`);
            const res = triggerCampaignBatch(device, app, cid);
            console.log(res.data);
            break;
        }

        case 'logs': {
            const lines = parseInt(flags.lines || '50', 10);
            console.log(`[*] Streaming Recent Logs (${lines} lines) for ${app.toUpperCase()} on ${device}...`);
            const res = getLogs(device, app, lines);
            console.log(res.lines.join('\n'));
            break;
        }

        default:
            console.error(`[Error] Unknown command: ${command}`);
            printUsage();
            process.exit(1);
    }
}

main();
