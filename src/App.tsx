import { useState, useEffect } from 'react';
import { registerPlugin } from '@capacitor/core';
import { Haptics, ImpactStyle } from '@capacitor/haptics';
import { Shield, Zap, Package, Folder, Terminal, Wifi, Usb, ChevronRight, Activity, Trash2, RefreshCw, Star, X, AlertCircle, Loader2, Cpu, Send, FileText, Smartphone, Ban, PowerOff } from 'lucide-react';

// Standalone Shizuku Plugin Definition
interface ShizukuExecutorPlugin {
  checkShizuku(): Promise<{ available: boolean, permission: boolean }>;
  executeCommand(options: { command: string }): Promise<{ output: string, code: number }>;
}
const ShizukuExecutor = registerPlugin<ShizukuExecutorPlugin>('ShizukuExecutor');

type Tab = 'dashboard' | 'packages' | 'files' | 'terminal' | 'help';

export default function App() {
  const [status, setStatus] = useState('OFFLINE');
  const [activeTab, setActiveTab] = useState<Tab>('dashboard');
  const [output, setOutput] = useState('');
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [isExecuting, setIsExecuting] = useState(false);
  const [currentActionId, setCurrentActionId] = useState<string | null>(null);
  
  const [packages, setPackages] = useState<{name: string, status: 'WORKING' | 'SLEEPING' | 'DISABLED'}[]>([]);
  const [expandedSections, setExpandedSections] = useState<Record<string, boolean>>({
    'ИСПОЛЬЗУЕМЫЕ (RUNNING)': true,
    'ЗАМОРОЖЕННЫЕ (FROZEN)': false,
    'ОТКЛЮЧЕННЫЕ (DISABLED)': false
  });
  const [searchPkg, setSearchPkg] = useState('');
  const [filePath, setFilePath] = useState('/sdcard/');
  const [customCmd, setCustomCmd] = useState('');
  
  const cyan = '#22d3ee';
  const lime = '#84cc16';
  const amber = '#f59e0b';
  const emerald = '#10b981';
  const danger = '#ef4444';

  const hapticClick = async () => {
    try { await Haptics.impact({ style: ImpactStyle.Medium }); } catch (e) {}
  };

  const getTime = () => {
    const d = new Date();
    return `[${d.toLocaleTimeString('en-GB', { hour12: false })}.${d.getMilliseconds().toString().padStart(3,'0')}]`;
  };

  const logTerm = (msg: string, type: 'info' | 'crit' | 'ok' | 'warn' = 'info') => {
    let prefix = '>';
    if (type === 'crit') prefix = '[!]';
    if (type === 'ok') prefix = '[+]';
    if (type === 'warn') prefix = '[*]';
    setOutput(prev => prev + `\n${getTime()} ${prefix} ${msg}`);
  };

  const callApi = async (action: string, params: any = {}, actionId: string | null = null) => {
    hapticClick();
    setIsExecuting(true);
    const idToTrack = actionId || action;
    setCurrentActionId(idToTrack);
    setErrorMsg(null);
    logTerm(`STANDALONE_EXEC_${idToTrack.toUpperCase()}...`, 'warn');
    
    let command = '';
    switch (action) {
      case 'freeze': command = `pm disable-user --user 0 ${params.package} || pm disable ${params.package}`; break;
      case 'unfreeze': command = `pm enable ${params.package}`; break;
      case 'force_stop': command = `am force-stop ${params.package}`; break;
      case 'uninstall': command = `pm uninstall --user 0 ${params.package}`; break;
      case 'dir_size': command = `du -sh "${params.path || '/sdcard/'}"`; break;
      case 'list_dir': command = `ls -lah "${params.path || '/sdcard/'}"`; break;
      case 'shell': command = params.command; break;
      default:
        logTerm('LOCAL_COMMAND_NOT_FOUND', 'crit');
        setIsExecuting(false);
        return;
    }

    try {
      const res = await ShizukuExecutor.executeCommand({ command });
      if (res.code === 0) {
        logTerm(`SUCCESS\n${res.output || 'DONE'}`, 'ok');
        if (['freeze', 'unfreeze', 'uninstall', 'force_stop'].includes(action)) {
            setTimeout(loadPackages, 1000);
        }
      } else {
        logTerm(`LOCAL_ERROR: ${res.output}`, 'crit');
        setErrorMsg('EXEC_FAILED');
      }
    } catch (e: any) {
      logTerm(`SHIZUKU_ERROR: ${e.message.toUpperCase()}`, 'crit');
      setErrorMsg('LINK_FAILED');
    } finally {
      setIsExecuting(false);
      setCurrentActionId(null);
    }
  };

  const loadPackages = async () => {
    hapticClick();
    setIsExecuting(true);
    setCurrentActionId('list_packages');
    logTerm('SCANNING_LOCAL_PACKAGES...', 'warn');
    try {
      const resEnabled = await ShizukuExecutor.executeCommand({ command: "pm list packages -e" });
      const resDisabled = await ShizukuExecutor.executeCommand({ command: "pm list packages -d" });
      
      const enabled = (resEnabled.output || '').split('\n')
        .filter(l => l.includes('package:'))
        .map(l => ({ name: l.replace('package:', '').trim(), status: 'WORKING' as const }));
        
      const disabled = (resDisabled.output || '').split('\n')
        .filter(l => l.includes('package:'))
        .map(l => ({ name: l.replace('package:', '').trim(), status: 'DISABLED' as const }));

      const all = [...enabled, ...disabled].sort((a,b) => a.name.localeCompare(b.name));
      setPackages(all);
      logTerm(`FOUND ${all.length} PACKAGES`, 'ok');
    } catch (e:any) {
      logTerm('LOCAL_SYNC_FAILED', 'crit');
    } finally {
      setIsExecuting(false);
      setCurrentActionId(null);
    }
  };

  const checkStatus = async () => {
    try {
      const res = await ShizukuExecutor.checkShizuku();
      if (res.available && res.permission) {
        if (status !== 'ONLINE') logTerm('SHIZUKU_SERVICE_CONNECTED', 'ok');
        setStatus('ONLINE');
      } else if (res.available && !res.permission) {
        setStatus('PENDING');
        logTerm('AWAITING_SHIZUKU_PERM', 'warn');
      } else {
        setStatus('OFFLINE');
      }
    } catch {
      setStatus('OFFLINE');
    }
  };

  useEffect(() => { 
    checkStatus();
    const t = setInterval(checkStatus, 5000);
    return () => clearInterval(t);
  }, []);

  const CyberButton = ({ onClick, children, id, fullWidth = false, color = cyan, className = "", activeColor = "#fff" }: any) => {
    const isThisExecuting = isExecuting && currentActionId === id;
    return (
      <button 
        onClick={() => { hapticClick(); onClick(); }} 
        disabled={isExecuting && !isThisExecuting}
        className={`cyber-border ${className}`}
        style={{
          width: fullWidth ? '100%' : 'auto',
          padding: '12px 10px',
          opacity: isExecuting && !isThisExecuting ? 0.4 : 1,
          background: isThisExecuting ? activeColor : 'rgba(15, 23, 42, 0.6)',
          color: isThisExecuting ? '#020617' : color,
          borderColor: isThisExecuting ? activeColor : color,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          gap: '8px',
          fontFamily: "'Space Mono', monospace",
          fontSize: '11px',
          fontWeight: 'bold',
          transition: 'all 0.15s'
        }}
      >
        {isThisExecuting ? <Loader2 size={14} className="animate-spin" /> : null}
        {children}
      </button>
    );
  };

  const NavTab = ({ id, icon: Icon, label }: { id: Tab, icon: any, label: string }) => (
    <button 
      onClick={() => { hapticClick(); setActiveTab(id); }} 
      style={{ 
        flex: 1, padding: '10px 2px', 
        background: activeTab === id ? 'rgba(34, 211, 238, 0.1)' : 'transparent', 
        color: activeTab === id ? cyan : '#475569', 
        border: 'none',
        borderBottom: activeTab === id ? `2px solid ${cyan}` : '2px solid transparent',
        display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '4px'
      }}
    >
      <Icon size={16} color={activeTab === id ? cyan : '#475569'} />
      <span style={{ fontSize: '9px', fontWeight: 'bold', fontFamily: "'Space Mono', monospace", letterSpacing: '1px' }}>{label}</span>
    </button>
  );

  const groupedPackages = {
    'ИСПОЛЬЗУЕМЫЕ (RUNNING)': packages.filter(p => p.status === 'WORKING'),
    'ЗАМОРОЖЕННЫЕ (FROZEN)': packages.filter(p => p.status === 'SLEEPING'),
    'ОТКЛЮЧЕННЫЕ (DISABLED)': packages.filter(p => p.status === 'DISABLED')
  };

  const toggleSection = (name: string) => {
    setExpandedSections(prev => ({ ...prev, [name]: !prev[name] }));
  };

  return (
    <div style={{ background: '#020617', color: '#cbd5e1', height: '100vh', display: 'flex', flexDirection: 'column', overflow: 'hidden', fontFamily: "'IBM Plex Mono', monospace" }}>
      <div className="scanline"></div>
      
      <header style={{ background: '#0f172a', borderBottom: '1px solid #1e293b', height: '54px', display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '0 15px', shrink: 0, zIndex: 10 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
          <span style={{ color: cyan, fontSize: '20px' }}>⚡</span>
          <h1 style={{ fontFamily: "'Syne', sans-serif", fontWeight: 800, fontSize: '15px', color: '#fff', letterSpacing: '2px', textShadow: `0 0 8px ${cyan}` }}>ADB STUDIO [SA]</h1>
        </div>
        
        <div style={{ display: 'flex', alignItems: 'center', gap: '15px' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
            <div style={{ width: '6px', height: '6px', borderRadius: '50%', background: status === 'ONLINE' ? emerald : (status === 'PENDING' ? amber : danger) }} className={status === 'ONLINE' ? 'animate-pulse' : ''}></div>
            <span style={{ fontSize: '10px', fontFamily: "'Space Mono', monospace", color: status === 'ONLINE' ? emerald : (status === 'PENDING' ? amber : danger) }}>{status}</span>
          </div>
          <button onClick={() => { hapticClick(); setActiveTab('help'); }} style={{ background: 'transparent', border: 'none', color: cyan }}>
            <Star size={20} fill={activeTab === 'help' ? cyan : 'transparent'} />
          </button>
        </div>
      </header>

      <main style={{ flexGrow: 1, display: 'flex', flexDirection: 'column', padding: '12px', overflow: 'hidden', zIndex: 10 }}>
        {activeTab !== 'help' && (
          <div style={{ display: 'flex', gap: '2px', background: '#090f1a', border: '1px solid #1e293b', marginBottom: '12px' }}>
              <NavTab id="dashboard" icon={Shield} label="AUDIT" />
              <NavTab id="packages" icon={Package} label="APPS" />
              <NavTab id="files" icon={Folder} label="FS" />
              <NavTab id="terminal" icon={Terminal} label="SHELL" />
          </div>
        )}

        <div className="scrollbar-hide" style={{ flexGrow: 1, overflowY: 'auto' }}>
          
          {activeTab === 'help' && (
            <div className="cyber-border warning" style={{ padding: '20px', background: 'rgba(245, 158, 11, 0.05)' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '20px' }}>
                    <h2 style={{ fontFamily: "'Space Mono', monospace", color: amber, fontSize: '14px', fontWeight: 'bold' }}>{"> "}STANDALONE_HELP</h2>
                    <X size={24} onClick={() => setActiveTab('dashboard')} color={amber} />
                </div>
                <div style={{ fontSize: '11px', color: '#94a3b8', lineHeight: 1.8, fontFamily: "'IBM Plex Mono', monospace" }}>
                   [1] Установите приложение **Shizuku** на телефон.<br/>
                   [2] Запустите его через Wireless Debugging.<br/>
                   [3] Разрешите доступ ADB Studio в окне Shizuku.<br/>
                   [4] Теперь вы можете управлять приложениями БЕЗ КОМПЬЮТЕРА.
                </div>
            </div>
          )}

          {activeTab === 'dashboard' && (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '15px' }}>
                <div className="cyber-border" style={{ padding: '15px' }}>
                    <h2 style={{ fontSize: '11px', fontWeight: 'bold', marginBottom: '15px', color: lime, fontFamily: "'Space Mono', monospace" }}>{"> "}LOCAL_AUDIT</h2>
                    <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '8px' }}>
                        <CyberButton onClick={() => callApi('shell', { command: 'getprop ro.build.type' }, 'audit_build')} id="audit_build" fullWidth color={lime}>BUILD_PROP</CyberButton>
                        <CyberButton onClick={() => callApi('shell', { command: 'pm list features' }, 'audit_hw')} id="audit_hw" fullWidth color={lime}>HW_MAP</CyberButton>
                        <CyberButton onClick={() => callApi('shell', { command: 'dumpsys battery' }, 'audit_bat')} id="audit_bat" fullWidth color={lime}>BATTERY</CyberButton>
                        <CyberButton onClick={() => callApi('shell', { command: 'id' }, 'audit_id')} id="audit_id" fullWidth color={lime}>LOCAL_UID</CyberButton>
                    </div>
                </div>

                <div className="cyber-border" style={{ padding: '15px' }}>
                    <h2 style={{ fontSize: '11px', fontWeight: 'bold', marginBottom: '15px', color: cyan, fontFamily: "'Space Mono', monospace" }}>{"> "}STANDALONE_MODE</h2>
                    <div style={{ padding: '10px', background: 'rgba(34, 211, 238, 0.05)', border: '1px solid #1e293b', fontSize: '10px', color: '#94a3b8' }}>
                        Работает через Shizuku. Компьютер и сервер больше не нужны. Все команды исполняются локально.
                    </div>
                </div>
            </div>
          )}

          {activeTab === 'packages' && (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
              <div style={{ display: 'flex', gap: '5px' }}>
                <input style={{ flex: 1, background: '#020617', color: cyan, border: '1px solid #1e293b', padding: '12px', outline: 'none', fontFamily: "'Space Mono', monospace", fontSize: '11px' }} placeholder="FILTER..." value={searchPkg} onChange={e => setSearchPkg(e.target.value)} />
                <CyberButton onClick={loadPackages} id="list_packages"><RefreshCw size={16} /></CyberButton>
              </div>
              
              <div style={{ display: 'flex', flexDirection: 'column', gap: '8px', marginTop: '5px' }}>
                  {Object.entries(groupedPackages).map(([groupName, pkgs]) => {
                      const filteredPkgs = pkgs.filter(p => p.name.includes(searchPkg));
                      const isExpanded = expandedSections[groupName] || searchPkg.length > 0;
                      
                      let groupColor = cyan;
                      if (groupName.includes('RUNNING')) groupColor = emerald;
                      if (groupName.includes('DISABLED')) groupColor = danger;
                      if (groupName.includes('FROZEN')) groupColor = amber;

                      return (pkgs.length > 0 || searchPkg.length === 0) && (
                          <div key={groupName} className="cyber-border" style={{ borderColor: '#1e293b' }}>
                              <button onClick={() => toggleSection(groupName)} style={{ width: '100%', padding: '10px', display: 'flex', justifyContent: 'space-between', alignItems: 'center', background: 'rgba(15, 23, 42, 0.8)', borderBottom: isExpanded ? '1px solid #1e293b' : 'none' }}>
                                  <span style={{ fontSize: '11px', fontFamily: "'Space Mono', monospace", fontWeight: 'bold', color: groupColor }}>{groupName} [{filteredPkgs.length}]</span>
                                  <ChevronRight size={16} color={groupColor} style={{ transform: isExpanded ? 'rotate(90deg)' : 'none', transition: 'transform 0.2s' }} />
                              </button>
                              {isExpanded && (
                                  <div style={{ padding: '8px', display: 'flex', flexDirection: 'column', gap: '4px', maxHeight: '400px', overflowY: 'auto' }}>
                                      {filteredPkgs.map(pkg => (
                                          <div key={pkg.name} style={{ padding: '10px', border: '1px solid #1e293b', background: '#020617', display: 'flex', flexDirection: 'column', gap: '8px' }}>
                                              <span style={{ fontSize: '10px', overflow: 'hidden', textOverflow: 'ellipsis', color: '#cbd5e1', fontWeight: 'bold' }}>{pkg.name}</span>
                                              <div style={{ display: 'flex', gap: '4px' }}>
                                                  <button onClick={() => callApi('force_stop', { package: pkg.name }, `stop_${pkg.name}`)} style={{ flex: 1, background: 'rgba(245, 158, 11, 0.1)', color: amber, border: `1px solid ${amber}`, padding: '6px', fontSize: '9px' }}>STOP</button>
                                                  {pkg.status !== 'DISABLED' ? (
                                                      <button onClick={() => callApi('freeze', { package: pkg.name }, `off_${pkg.name}`)} style={{ flex: 1, background: 'rgba(239, 68, 68, 0.1)', color: danger, border: `1px solid ${danger}`, padding: '6px', fontSize: '9px' }}>OFF</button>
                                                  ) : (
                                                      <button onClick={() => callApi('unfreeze', { package: pkg.name }, `on_${pkg.name}`)} style={{ flex: 1, background: 'rgba(16, 185, 129, 0.1)', color: emerald, border: `1px solid ${emerald}`, padding: '6px', fontSize: '9px' }}>ON</button>
                                                  )}
                                                  <button onClick={() => callApi('uninstall', { package: pkg.name }, `del_${pkg.name}`)} style={{ flex: 1, background: 'rgba(255, 255, 255, 0.05)', color: '#fff', border: '1px solid #475569', padding: '6px', fontSize: '9px' }}>DEL</button>
                                              </div>
                                          </div>
                                      ))}
                                  </div>
                              )}
                          </div>
                      );
                  })}
              </div>
            </div>
          )}

          {activeTab === 'files' && (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '15px' }}>
                <div className="cyber-border warning" style={{ padding: '15px' }}>
                    <h2 style={{ fontSize: '11px', fontWeight: 'bold', color: amber, marginBottom: '10px' }}>{"> "}LOCAL_FS</h2>
                    <div style={{ display: 'flex', gap: '5px', marginBottom: '10px' }}>
                        <input style={{ flex: 1, background: '#020617', color: amber, border: '1px solid #78350f', padding: '12px', outline: 'none', fontSize: '11px' }} value={filePath} onChange={e => setFilePath(e.target.value)} />
                        <CyberButton onClick={() => callApi('list_dir', { path: filePath }, 'list_dir')} id="list_dir" color={amber}><RefreshCw size={16} /></CyberButton>
                    </div>
                    <div style={{ display: 'grid', gridTemplateColumns: '1fr', gap: '8px' }}>
                        <CyberButton onClick={() => callApi('dir_size', { path: filePath }, 'dir_size')} id="dir_size" color={amber} fullWidth>CALCULATE_SIZE</CyberButton>
                        <CyberButton onClick={() => callApi('shell', { command: `cat "${filePath}"` }, 'file_cat')} id="file_cat" color={cyan} fullWidth>READ_CONTENT</CyberButton>
                    </div>
                </div>
            </div>
          )}

          {activeTab === 'terminal' && (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '15px' }}>
                <div className="cyber-border" style={{ padding: '15px' }}>
                    <h2 style={{ fontSize: '11px', fontWeight: 'bold', color: cyan, marginBottom: '10px' }}>{"> "}LOCAL_SHELL</h2>
                    <textarea style={{ width: '100%', height: '120px', background: '#020617', color: '#e2e8f0', border: '1px solid #1e293b', padding: '12px', outline: 'none', fontSize: '11px', resize: 'none' }} placeholder="Enter shell command..." value={customCmd} onChange={e => setCustomCmd(e.target.value)} />
                    <div style={{ marginTop: '10px' }}>
                        <CyberButton onClick={() => callApi('shell', { command: customCmd }, 'shell_exec')} id="shell_exec" fullWidth color={cyan}><Send size={14} /> EXECUTE</CyberButton>
                    </div>
                </div>
            </div>
          )}
        </div>

        {activeTab !== 'packages' && (
          <div className="cyber-border" style={{ height: '240px', background: 'rgba(2, 6, 23, 0.95)', borderTop: `1px solid ${cyan}`, marginTop: '12px', padding: '0', display: 'flex', flexDirection: 'column' }}>
              <div style={{ padding: '8px 12px', borderBottom: '1px solid #1e293b', display: 'flex', justifyContent: 'space-between', alignItems: 'center', background: '#0f172a' }}>
                  <span style={{ fontSize: '9px', color: cyan, fontWeight: 'bold' }}>{"> "}STANDALONE_LOG_STREAM</span>
                  <button onClick={() => { hapticClick(); setOutput(''); }} style={{ background: 'transparent', border: 'none', color: '#64748b', fontSize: '9px' }}>[CLEAR]</button>
              </div>
              <div style={{ padding: '10px', overflowY: 'auto', flexGrow: 1, display: 'flex', flexDirection: 'column-reverse' }}>
                  <pre style={{ margin: 0, whiteSpace: 'pre-wrap', fontSize: '10px', color: '#cbd5e1', fontFamily: "'IBM Plex Mono', monospace", lineHeight: 1.5 }}>{output || 'AWAITING_LOCAL_ACTION...'}</pre>
              </div>
          </div>
        )}
      </main>
    </div>
  );
}
