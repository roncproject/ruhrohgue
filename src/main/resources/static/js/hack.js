/* RuhRohgue — Cloud Edition | JS v22 */
'use strict';

const COLS = 80, ROWS = 22;
let gameActive = false, isOnline = navigator.onLine, offlineQueue = [];
let autoPickup = false, lastDir = {dx:0,dy:0};
let confirmMode = null;      // null | 'N' | 'Q'
let chooserMode = null;      // null | 'wield'|'wear'|'takeoff'|'eat'|'ring'|'ringremove'|'drop'
let chooserItems = [];

// ── Grid ──────────────────────────────────────────────────────────────────
const CELLS = [];
const grid  = document.getElementById('dungeon-grid');
(function buildGrid() {
  const frag = document.createDocumentFragment();
  for (let x=0;x<COLS;x++){CELLS[x]=[];for(let y=0;y<ROWS;y++){
    const s=document.createElement('span');
    s.id='cell-'+x+'-'+y; s.className='cell';
    s.style.cssText='grid-column:'+(x+1)+';grid-row:'+(y+1);
    s.dataset.cell=x+','+y; s.dataset.type='empty'; s.dataset.sym=' ';
    s.setAttribute('role','img'); s.setAttribute('aria-label','unseen');
    s.textContent=' '; frag.appendChild(s); CELLS[x][y]=s;
  }}
  grid.appendChild(frag);
})();

// ── Floating overlay factory ──────────────────────────────────────────────
function makeOverlay(id, extraStyle) {
  const d = document.createElement('div');
  d.id = id;
  d.style.cssText = [
    'display:none','position:fixed','z-index:600','user-select:none',
    'background:#111','border:1px solid #aaa','padding:10px 14px',
    'font-family:monospace','font-size:0.82rem','color:#ccc',
    'min-width:260px','max-height:75vh','overflow-y:auto',
    'right:4px','top:50%','transform:translateY(-50%)'
  ].concat(extraStyle||[]).join(';');
  document.body.appendChild(d);
  return d;
}

const chooserEl  = makeOverlay('item-chooser');
const invEl      = makeOverlay('inv-overlay', ['min-width:300px']);
const lookEl     = makeOverlay('look-overlay', ['min-width:280px']);
const goldDialog = makeOverlay('gold-dialog', ['min-width:260px','text-align:center']);
const shopTabEl  = makeOverlay('shop-tab', ['min-width:320px','max-width:420px']);

// ── Shop tab state ────────────────────────────────────────────────────────
var _shopTabItems = [];
var _shopTabSelected = new Set();
var _shopGold = 0, _shopBank = 0;


function showChooser(mode, items, title) {
  chooserMode = mode; chooserItems = items;
  var html = '<div style="color:#ffb000;margin-bottom:6px">'+esc(title)+'</div>';
  items.forEach(function(it,i){
    var letter = String.fromCharCode(97+i);
    // The Eat menu must show the same [unpaid] marker as the inventory, so
    // the player can tell shop stock from their own food before eating it.
    html += '<div><span style="color:#88f">'+letter+')</span> '
          + esc(it.sym||'') + ' ' + esc(it.name||it)
          + (it.worn?' <span style="color:#8f8">('+esc(it.worn)+')</span>':'')
          + (it.rot?' <span style="color:#c86">('+esc(it.rot)+')</span>':'')
          + (it.unpaid?' <span style="color:#f66">[unpaid]</span>':'')
          + '</div>';
  });
  html += '<div style="margin-top:6px;color:#666">Esc to cancel</div>';
  chooserEl.innerHTML = html; chooserEl.style.display = 'block';
  document.getElementById('layout').focus();
}
function hideChooser() {
  chooserMode = null; chooserItems = [];
  chooserEl.style.display = 'none';
}

// Floating inventory overlay
function showInvOverlay() {
  var inv = applyState._inv || [];
  var html = '<div style="color:#ffb000;font-weight:bold;margin-bottom:8px">INVENTORY</div>';
  if (!inv.length) { html += '<div style="color:#888">Empty</div>'; }
  inv.forEach(function(it){
    html += '<div><span style="color:#88f">'+esc(it.slot)+')</span> '
          + '<span style="color:#ddd">'+esc(it.sym)+'</span> '
          + esc(it.name)
          + (it.worn?' <span style="color:#8f8">('+esc(it.worn)+')</span>':'')
          + '</div>';
  });
  html += '<div style="margin-top:8px;color:#666">Esc to close</div>';
  invEl.innerHTML = html; invEl.style.display = 'block';
  document.getElementById('layout').focus();
}
function hideInvOverlay() { invEl.style.display = 'none'; }

// ':' look-here overlay
function showLookOverlay(items) {
  var html='<div style="color:#ffb000;font-weight:bold;margin-bottom:6px">';
  html+='FLOOR TILE CONTENTS</div>';
  if(!items||!items.length){
    html+='<div style="color:#888">Nothing here.</div>';
  } else {
    items.forEach(function(s){html+='<div>• '+esc(s)+'</div>';});
  }
  html+='<div style="margin-top:8px;color:#666">Esc to close</div>';
  lookEl.innerHTML=html; lookEl.style.display='block';
  document.getElementById('layout').focus();
}
function hideLookOverlay(){ lookEl.style.display='none'; }

// Gold amount dialog
function showGoldDialog(title, maxAmt, onOk) {
  goldDialog.innerHTML =
    '<div style="color:#ffb000;margin-bottom:8px">'+esc(title)+'</div>' +
    '<input id="gold-amt" type="number" min="0" max="'+maxAmt+'" value="0" '+
    'style="width:80%;background:#222;border:1px solid #aaa;color:#eee;padding:4px;font-size:1rem"/>' +
    '<br><br><button id="gold-ok" style="background:#333;border:1px solid #aaa;color:#eee;'+
    'padding:6px 18px;cursor:pointer;font-size:0.9rem">OK</button>' +
    ' <button id="gold-cancel" style="background:#333;border:1px solid #aaa;color:#888;'+
    'padding:6px 12px;cursor:pointer;font-size:0.9rem">Cancel</button>';
  goldDialog.style.display = 'block';
  document.getElementById('gold-amt').focus();
  document.getElementById('gold-ok').onclick = function() {
    var raw = document.getElementById('gold-amt').value;
    var v = parseInt(raw, 10);
    if (isNaN(v) || v < 0) v = 0;
    if (v > maxAmt) v = maxAmt;
    goldDialog.style.display = 'none';
    document.getElementById('layout').focus();  // restore keyboard focus
    onOk(v);
  };
  document.getElementById('gold-cancel').onclick = function() {
    goldDialog.style.display = 'none';
    document.getElementById('layout').focus();  // restore keyboard focus
  };
  var gInput = document.getElementById('gold-amt');
  gInput.oninput = function() {
    var v = parseInt(this.value, 10);
    if (isNaN(v) || v < 0) this.value = 0;
    if (v > maxAmt) this.value = maxAmt;
  };
  gInput.onkeydown = function(e) {
    if (e.key==='Enter') document.getElementById('gold-ok').click();
    if (e.key==='Escape') document.getElementById('gold-cancel').click();
  };
}

// ── Sym → type ────────────────────────────────────────────────────────────
function symToType(ch) {
  switch(ch) {
    case '@': return 'player'; case '.': return 'floor'; case '#': return 'corridor';
    case '-': return 'wall-h'; case '|': return 'wall-v'; case '+': return 'door';
    // ':' removed as door symbol — open doors now show as ' ' (floor/space)
    case '<': return 'stairs-up'; case '>': return 'stairs-down';
    case '}': return 'pool'; case '$': return 'gold'; case '^': return 'trap'; case ' ': return 'empty';
    default:
      if ((ch>='A'&&ch<='Z')||(ch>='a'&&ch<='z')||ch==='&'||ch===';') return 'monster';
      return 'item';
  }
}
function ariaFor(t,s,n){
  if(t==='player')return'player'; if(t==='monster')return'monster: '+(n||s);
  if(t==='gold')return'gold: '+(n||'pile'); if(t==='door-open')return'open door';
  if(t==='stairs-up')return'stairs up'; if(t==='stairs-down')return'stairs down';
  if(t==='empty')return'unseen'; return t;
}
function setCell(x,y,sym,type,name){
  var s=CELLS[x][y];
  s.textContent=sym; s.dataset.type=type; s.dataset.sym=sym;
  if(name)s.dataset.name=name;else delete s.dataset.name;
  s.setAttribute('aria-label',ariaFor(type,sym,name));
}
function blankCell(x,y){
  var s=CELLS[x][y];s.textContent=' ';s.dataset.type='empty';s.dataset.sym=' ';
  delete s.dataset.name;s.setAttribute('aria-label','unseen');
}

// ── Render ────────────────────────────────────────────────────────────────
function renderMap(map,monsters,items,gold,traps,px,py){
  for(var x=0;x<COLS;x++)for(var y=0;y<ROWS;y++){
    var ch=(map[x]&&map[x][y])?map[x][y]:' ';
    if(ch===' '||ch==='@')blankCell(x,y);else setCell(x,y,ch,symToType(ch),null);
  }
  gold.forEach(function(g){setCell(g.x,g.y,'$','gold',g.amount+' gold');});
  traps.forEach(function(t){setCell(t.x,t.y,'^','trap',t.desc||'trap');});
  items.forEach(function(i){setCell(i.x,i.y,i.sym,'item',i.name);});
  monsters.forEach(function(m){setCell(m.x,m.y,m.sym,'monster',m.name);});
  setCell(px,py,'@','player','player');
}

// ── Apply state ───────────────────────────────────────────────────────────
function applyState(d) {
  if(!d||d.error)return;
  var p=d.player||{};

  // Control strings never reach the message bar — only real game text does.
  if(d.topMsg && d.topMsg.charAt(0)!=='[')
    document.getElementById('message-bar').textContent=d.topMsg;

  var msgs = d.messages||[];

  // ── One-shot UI signal ──────────────────────────────────────────────────
  // The server clears 'signal' as soon as it is serialised, so each overlay
  // opens exactly once. Previously these control strings lived in the message
  // log, where they stayed at index 0 and re-triggered the overlay on every
  // later state refresh (the "look keeps reopening" bug).
  var sig = d.signal || '';

  if(sig.startsWith('[bank:pickup:')) {
    var parts = sig.replace('[bank:pickup:','').replace(']','').split(':');
    var floorAmt = parseInt(parts[0],10)||0, bankAmt = parseInt(parts[1],10)||0;
    if(bankAmt <= 0) {
      setTimeout(function(){ sendCommand('vault_no_credit'); }, 0);
    } else {
      var maxPick = Math.min(floorAmt, bankAmt);
      showGoldDialog('Pick up how much gold? (0-'+maxPick+')', maxPick, function(v){
        if(v>0 && v<=maxPick) sendCommand('pickupvault:'+v);
        else if(v>maxPick) sendCommand('vault_no_credit');
      });
    }
  }
  if(sig.startsWith('[Choose a ring to put on]')) {
    var rings = (applyState._inv||[]).filter(function(it){return it.sym==='=';});
    if(rings.length) showChooser('ring', rings, 'Put on which ring? (a-'+String.fromCharCode(97+rings.length-1)+')');
  }
  if(sig.startsWith('[Choose which ring to remove]')) {
    var worn = (applyState._inv||[]).filter(function(it){return it.worn&&it.sym==='=';});
    if(worn.length) showChooser('ringremove', worn, 'Remove which ring? (a-'+String.fromCharCode(97+worn.length-1)+')');
  }
  if(sig.startsWith('[What do you want to drop?]')) {
    showDropChooser();
  }

  if(sig.startsWith('[shoptab:')) {
    var tabRaw = sig.slice(9, -1).split('|').filter(Boolean);
    var tabItems = tabRaw.map(function(entry) {
      var parts = entry.split(':');
      return { name: parts[0], price: parseInt(parts[1]) || 1 };
    });
    showShopTab(tabItems);
  }

  // ':' look-here. Automatic tile contents are now ordinary message lines
  // written by the server ("You see here: ..."), so there is no [autolook:]
  // control string to decode any more.
  if(sig.startsWith('[look:')) {
    var lookContent = sig.slice(6,-1).split('|').filter(Boolean);
    showLookOverlay(lookContent);
  }

  if(sig==='[throw_menu]') {
    showThrowChooser();
  }

  document.getElementById('s-name').textContent  = p.name   ||'\u2014';
  document.getElementById('s-class').textContent = p.class  ||'\u2014';
  document.getElementById('s-level').textContent = p.level  ||1;
  document.getElementById('s-exp').textContent   = p.exp    ||0;
  var hpEl=document.getElementById('s-hp');
  hpEl.textContent=p.hp+'('+p.hpMax+')';
  hpEl.className=(p.hp/p.hpMax<0.25)?'stat-hp-low':'stat-hp';
  document.getElementById('s-ac').textContent    =(p.ac!=null)?p.ac:10;
  document.getElementById('s-str').textContent   =p.str    ||'\u2014';
  document.getElementById('s-gold').textContent  =p.gold   ||0;
  // Bank
  var bankEl=document.getElementById('s-bank');
  if(bankEl) bankEl.textContent=p.bank||0;
  document.getElementById('s-hunger').textContent=p.hunger ||'\u2014';
  document.getElementById('s-dlevel').textContent=d.dlevel ||1;
  document.getElementById('move-counter').textContent='Turn: '+(d.moves||0);

  var conds=[];
  if(p.blind)conds.push('Blind'); if(p.confused)conds.push('Conf');
  if(p.levitating)conds.push('Lev'); if(p.invisible)conds.push('Invis');
  if(autoPickup)conds.push('AutoPick');
  document.getElementById('s-cond').textContent=conds.join(' ')||'\u2014';

  var invListEl=document.getElementById('inv-list');
  invListEl.innerHTML='';
  (d.inventory||[]).forEach(function(it){
    if(it.strange) return; // hide internal placeholder items
    var li=document.createElement('li');
    li.innerHTML='<span class="slot">'+esc(it.slot)+')</span>'
      +'<span class="isym">'+esc(it.sym)+'</span>'
      +'<span class="iname">'+esc(it.name)+'</span>'
      +(it.worn?'<span class="iworn">'+esc(it.worn)+'</span>':'')
      +(it.unpaid?'<span style="color:#f84;font-size:0.8em"> [unpaid]</span>':'');
    invListEl.appendChild(li);
  });

  var logEl=document.getElementById('msg-log');
  logEl.innerHTML='';
  msgs.slice(0,15).forEach(function(m,i){
    var el=document.createElement('p');el.textContent=m;
    if(i===0)el.style.color='#ffb000';logEl.appendChild(el);
  });

  if(d.map&&p.x!==undefined)
    renderMap(d.map,d.monsters||[],d.items||[],d.gold||[],d.traps||[],p.x,p.y);

  applyState._inv=d.inventory||[];

  var phase=d.phase||'PLAYING';
  if(phase==='DEAD'||phase==='ESCAPED'||phase==='QUIT'){
    gameActive=false;
    var goScreen=document.getElementById('gameover-screen');
    var gt=document.getElementById('go-heading');
    gt.textContent=phase==='ESCAPED'?'You Escaped!':phase==='QUIT'?'You Quit':'You Died';
    gt.style.color=phase==='ESCAPED'?'#44ff88':'#ff3333';
    goScreen.classList.toggle('escaped',phase==='ESCAPED');
    document.getElementById('go-score').textContent ='Score: '+(d.score||0);
    var p2=d.player||{};
    document.getElementById('go-class').textContent =p2.characterClass?'You were a '+p2.characterClass:'';
    document.getElementById('go-exp').textContent   =p2.exp!=null?'You had '+p2.exp+' experience':'';
    document.getElementById('go-gold').textContent  =p2.gold!=null?'You had '+p2.gold+' gold':'';
    document.getElementById('go-floor').textContent =d.maxFloor!=null?'You reached floor '+d.maxFloor:'';
    document.getElementById('go-killer').textContent=
      phase==='ESCAPED'?'You reached the surface!':(d.killer?'Unalived by: '+d.killer:'');
    document.getElementById('go-moves').textContent ='Moves: '+(d.moves||0);

    // Magnificent 7 high score table
    var mag7El=document.getElementById('go-mag7');
    if(mag7El){
      if(d.highScores&&d.highScores.length){
        var hs='<div class="mag7-title">Magnificent 7</div><table class="mag7-table">'
          +'<tr><th>#</th><th>Name</th><th>Class</th><th>Floor</th><th>Score</th><th>Date</th></tr>';
        d.highScores.slice(0,7).forEach(function(e,i){
          hs+='<tr><td>'+(i+1)+'</td><td>'+esc(e.name)+'</td><td>'+esc(e.class)+'</td>'
            +'<td>'+e.floor+'</td><td>'+e.score+'</td><td>'+esc(e.time)+'</td></tr>';
        });
        hs+='</table>';
        mag7El.innerHTML=hs;
      } else {
        mag7El.innerHTML='';
      }
    }
    goScreen.style.display='flex';
  }
}
applyState._inv=[];

// ── API ───────────────────────────────────────────────────────────────────
async function startGame(name,role,seed){
  var body='name='+encodeURIComponent(name)+'&role='+encodeURIComponent(role);
  if(seed) body+='&seed='+encodeURIComponent(seed);
  try{
    var r=await fetch('/new',{method:'POST',body:body,headers:{'Content-Type':'application/x-www-form-urlencoded'}});
    var d=await r.json();
    // The server validates the name independently of the browser. A rejection
    // must be shown rather than swallowed, or the new-game screen appears to
    // do nothing at all.
    if(!r.ok||d.error){
      document.getElementById('message-bar').textContent=
        d.message||'Could not start the game.';
      var ni=document.getElementById('input-name');
      if(d.error==='invalid_name'&&ni){ni.style.outline='2px solid #f33';ni.focus();
        setTimeout(function(){ni.style.outline='';},1500);}
      return;
    }
    gameActive=true; lastDir={dx:0,dy:0}; confirmMode=null; hideChooser();
    document.getElementById('new-game-screen').style.display='none';
    document.getElementById('gameover-screen').style.display='none';
    applyState(d); document.getElementById('layout').focus();
  } catch(err){showError('Could not reach server: '+err.message);}
}

// How long to wait before retrying a rate-limited keypress, in ms.
var RETRY_BASE_MS = 250;

async function sendCommand(cmd, attempt){
  if(!gameActive)return;
  if(!isOnline){offlineQueue.push(cmd);document.getElementById('message-bar').textContent='[offline] Queued: '+cmd;return;}
  attempt = attempt || 0;
  try{
    var r=await fetch('/command',{method:'POST',body:'cmd='+encodeURIComponent(cmd),headers:{'Content-Type':'application/x-www-form-urlencoded'}});

    // ── Rate limited ────────────────────────────────────────────────────
    // The server answers 429 with {"error":"rate_limited"}, which is NOT a
    // game state. The previous code passed every response straight to
    // applyState(), so a throttled keypress fed an object with no map and no
    // messages into the renderer and blanked the screen — indistinguishable,
    // from the player's side, from the keyboard having locked up.
    // Retry once after the server's Retry-After, then give up quietly.
    if(r.status===429){
      var wait=parseInt(r.headers.get('Retry-After'),10);
      wait=(isNaN(wait)?RETRY_BASE_MS:Math.min(wait*1000,2000));
      if(attempt<1){
        await new Promise(function(res){setTimeout(res,wait);});
        return sendCommand(cmd,attempt+1);
      }
      document.getElementById('message-bar').textContent='Slow down a moment…';
      return;
    }

    if(!r.ok){
      document.getElementById('message-bar').textContent='Server error ('+r.status+').';
      return;
    }

    var d=await r.json();
    // A body without a map is an error payload, not a state. Never render it.
    if(!d||!d.map){
      if(d&&d.message)document.getElementById('message-bar').textContent=d.message;
      return;
    }
    applyState(d);
  } catch(err){offlineQueue.push(cmd);setOnlineStatus(false);}
}

async function flushOfflineQueue(){
  if(!offlineQueue.length||!gameActive)return;
  var q=offlineQueue.splice(0);
  for(var i=0;i<q.length;i++)await sendCommand(q[i]);
}

// ── Online/offline ────────────────────────────────────────────────────────
function setOnlineStatus(o){
  isOnline=o;document.getElementById('offline-banner').classList.toggle('visible',!o);
  if(o)flushOfflineQueue();
}
window.addEventListener('online', function(){setOnlineStatus(true);});
window.addEventListener('offline',function(){setOnlineStatus(false);});

// ── Tab visibility: re-sync state when tab regains focus ─────────────────
// Root cause of the "game freezes after idle" bug:
// When the browser tab is hidden, browsers throttle JS timers and some
// background fetch requests. If the HTTP session expired (server timeout)
// while the tab was hidden, the game state is lost. When the tab becomes
// visible again, we send a /state ping to refresh the session and re-apply
// the current game state without the player having to do anything.
document.addEventListener('visibilitychange', async function() {
  if (document.visibilityState === 'visible' && gameActive && isOnline) {
    try {
      var r = await fetch('/state', { method: 'GET' });
      if (r.ok) {
        var d = await r.json();
        if (d && !d.error) applyState(d);
      }
    } catch(e) {
      // Network error — will be caught by the online/offline handlers
      console.warn('[Hack] state resync failed on tab show:', e.message);
    }
  }
});

// ── Key/direction maps — BEFORE event listeners ───────────────────────────
var KEY_MAP={ArrowLeft:'h',ArrowRight:'l',ArrowUp:'k',ArrowDown:'j'};
var IGNORE_KEYS=new Set(['Shift','Control','Alt','Meta','CapsLock','Tab','Clear',
  'F1','F2','F3','F4','F5','F6','F7','F8','F9','F10','F11','F12',
  'Insert','Delete','Home','End','PageUp','PageDown']);
var DIR_DELTA={h:{dx:-1,dy:0},l:{dx:1,dy:0},k:{dx:0,dy:-1},j:{dx:0,dy:1},
               y:{dx:-1,dy:-1},u:{dx:1,dy:-1},b:{dx:-1,dy:1},n:{dx:1,dy:1}};

// ── Shop tab overlay ─────────────────────────────────────────────────────
function showShopTab(items) {
  // A fresh tab always starts with nothing selected; the server has already
  // reconciled the list against what the player is actually carrying, so
  // entries from an earlier visit can no longer appear here.
  _shopTabItems = items || []; _shopTabSelected = new Set();
  renderShopTab();
  shopTabEl.style.display = 'block';
  document.getElementById('layout').focus();
}
function renderShopTab() {
  var total = 0;
  _shopTabSelected.forEach(function(i){ total += _shopTabItems[i].price; });
  var html = '<div style="color:#ffb000;font-weight:bold;margin-bottom:8px">'
           + 'Shop Tab &mdash; select items to pay</div>';
  _shopTabItems.forEach(function(it, i) {
    var sel = _shopTabSelected.has(i);
    var letter = String.fromCharCode(97 + i);
    html += '<div style="cursor:pointer;color:' + (sel ? '#8f8' : '#ccc') + '" id="sti' + i + '">';
    html += '<span style="color:#88f">' + letter + ')</span> '
          + '<span style="color:' + (sel ? '#8f8' : '#666') + '">'
          + (sel ? '[x]' : '[ ]') + '</span> ' + esc(it.name)
          + '  <span style="color:#ff8">' + it.price + ' gold</span></div>';
  });
  html += '<div style="margin-top:8px;border-top:1px solid #555;padding-top:6px">';
  html += 'Total: <span style="color:#ff8" id="shop-total">' + total + '</span> gold</div>';
  html += '<div style="color:#888;font-size:0.85em;margin-top:2px">'
        + 'Gold ' + _shopGold + ' &nbsp;|&nbsp; Bank ' + _shopBank + '</div>';
  html += '<div style="color:#888;font-size:0.85em;margin-top:4px">'
        + 'Type a letter to select. Esc to close.</div>';
  html += '<div style="margin-top:8px">';
  html += '<button id="shop-cash" style="background:#333;border:1px solid #aaa;color:#eee;'
        + 'padding:5px 14px;cursor:pointer;margin-right:8px">Cash</button>';
  html += '<button id="shop-bank" style="background:#333;border:1px solid #aaf;color:#eee;'
        + 'padding:5px 14px;cursor:pointer;margin-right:8px">Bank</button>';
  html += '<button id="shop-cancel" style="background:#333;border:1px solid #666;color:#888;'
        + 'padding:5px 10px;cursor:pointer">Cancel</button></div>';
  shopTabEl.innerHTML = html;
  _shopTabItems.forEach(function(it, i) {
    var el = document.getElementById('sti' + i);
    if (el) el.onclick = function() {
      if (_shopTabSelected.has(i)) _shopTabSelected.delete(i);
      else _shopTabSelected.add(i);
      renderShopTab();
    };
  });
  function pay(method) {
    // No explicit selection means "settle the whole tab" rather than nothing
    // happening when the buttons are pressed.
    var sel = (_shopTabSelected.size === 0)
      ? _shopTabItems.map(function(_, i){ return i; })
      : Array.from(_shopTabSelected);
    if (sel.length === 0) return;
    var idxList = sel.sort(function(a,b){return a-b;}).join(',');
    shopTabEl.style.display = 'none';
    document.getElementById('layout').focus();
    sendCommand('payshop:' + method + ':' + idxList);
  }
  var btnCash = document.getElementById('shop-cash');
  var btnBank = document.getElementById('shop-bank');
  var btnCancel = document.getElementById('shop-cancel');
  if (btnCash)   btnCash.onclick   = function() { pay('cash'); };
  if (btnBank)   btnBank.onclick   = function() { pay('bank'); };
  if (btnCancel) btnCancel.onclick = function() {
    shopTabEl.style.display = 'none';
    document.getElementById('layout').focus();
  };
}
function hideShopTab() { shopTabEl.style.display = 'none'; }

// ── Document-level chooser interceptor (true capture phase) ───────────────
// Registered with capture=true so an open overlay always consumes the key
// before the #layout handler can forward it to the server. Without this the
// Escape key leaked through and came back as "Unknown command 'Escape'".
document.addEventListener('keydown',function(e){
  var anyOverlay = chooserMode || invEl.style.display!=='none' || lookEl.style.display!=='none' || shopTabEl.style.display!=='none';
  if(!anyOverlay)return;
  if(IGNORE_KEYS.has(e.key))return;
  if(e.ctrlKey||e.altKey||e.metaKey)return;
  e.preventDefault(); e.stopImmediatePropagation();
  if(shopTabEl.style.display!=='none'){
    if(e.key==='Escape'){hideShopTab();}
    else if(e.key.length===1&&e.key>='a'&&e.key<='z'){
      var shopIdx=e.key.charCodeAt(0)-97;
      if(shopIdx<_shopTabItems.length){
        if(_shopTabSelected.has(shopIdx))_shopTabSelected.delete(shopIdx);
        else _shopTabSelected.add(shopIdx);
        renderShopTab();
      }
    }
    return;
  }
  if(lookEl.style.display!=='none'){
    if(e.key==='Escape'||e.key===':')hideLookOverlay();
    return;
  }
  if(invEl.style.display!=='none'){
    if(e.key==='Escape'||e.key==='i'||e.key==='I')hideInvOverlay();
    return;
  }
  handleChooserKey(e.key);
},true);

// ── Chooser helpers ───────────────────────────────────────────────────────
function invEdible()   {return(applyState._inv||[]).filter(function(it){return it.sym==='%' && !it.strange;});}
function invWieldable(){return(applyState._inv||[]).filter(function(it){return it.sym===')'||it.sym==='('||it.sym==='/';}); }
function invWearable() {return(applyState._inv||[]).filter(function(it){return it.sym==='['||it.sym==='(';});}
function invWorn()     {return(applyState._inv||[]).filter(function(it){return!!it.worn;});}
function invRings()    {return(applyState._inv||[]).filter(function(it){return it.sym==='=';});}
function invWornRings(){return(applyState._inv||[]).filter(function(it){return it.sym==='='&&!!it.worn;});}

function handleChooserKey(key){
  if(key==='Escape'){hideChooser();return;}
  if(key.length!==1){return;}
  var code=key.charCodeAt(0);
  if(code<97||code>122){hideChooser();return;}
  var idx=code-97;
  if(idx>=chooserItems.length)return;
  var item=chooserItems[idx];
  var slot=(item&&item.slot!=null)?item.slot:item;
  var mode=chooserMode;
  hideChooser();  // reset chooserMode to null BEFORE any sendCommand

  if(mode==='drop'){
    if(slot==='__gold__'){
      var goldTxt=document.getElementById('s-gold').textContent;
      var maxG=parseInt(goldTxt,10)||0;
      showGoldDialog('Drop how much gold? (0-'+maxG+')',maxG,function(v){
        if(v>0)sendCommand('dropgold:'+v);
      });
    } else {
      sendCommand('drop:'+slot);
    }
    return;
  }

  if(mode==='throw'){
    var td=lastDir;
    if(td.dx===0 && td.dy===0){
      // No direction set — inform the player
      document.getElementById('message-bar').textContent='Move first to set a throw direction.';
      return;
    }
    if(slot==='__throwgold__'){
      var goldTxtT=document.getElementById('s-gold').textContent;
      var maxGT=parseInt(goldTxtT,10)||0;
      showGoldDialog('Throw how much gold? (0-'+maxGT+')',maxGT,function(v){
        if(v>0)sendCommand('throwgold:'+v+':'+td.dx+':'+td.dy);
      });
    } else {
      sendCommand('throw:'+slot+':'+td.dx+':'+td.dy);
    }
    return;
  }

  var map={wield:'wield:',wear:'wear:',takeoff:'takeoff:',eat:'eat:',
           ring:'putring:',ringremove:'removering:'};
  sendCommand((map[mode]||'')+slot);
}

function doWield(){var it=invWieldable();if(!it.length){sendCommand('wield_nothing');return;}
  showChooser('wield',it,'Wield what? (a-'+String.fromCharCode(97+it.length-1)+')');}
function doWear(){var it=invWearable();if(!it.length){sendCommand('wear_nothing');return;}
  showChooser('wear',it,'Wear what? (a-'+String.fromCharCode(97+it.length-1)+')');}
function doTakeoff(){var it=invWorn();if(!it.length){sendCommand('takeoff_nothing');return;}
  showChooser('takeoff',it,'Take off what? (a-'+String.fromCharCode(97+it.length-1)+')');}
function doEat(){var it=invEdible();if(!it.length){sendCommand('eat_nothing');return;}
  showChooser('eat',it,'Eat what? (a-'+String.fromCharCode(97+it.length-1)+')');}
function doPutRing(){var it=invRings();if(!it.length){sendCommand('ring_nothing');return;}
  showChooser('ring',it,'Put on which ring? (a-'+String.fromCharCode(97+it.length-1)+')');}
function doRemoveRing(){var it=invWornRings();if(!it.length){sendCommand('noringworn');return;}
  showChooser('ringremove',it,'Remove which ring? (a-'+String.fromCharCode(97+it.length-1)+')');}

// Drop chooser: Gold + inventory
function showDropChooser(){
  var p=applyState._inv||[];
  var goldEntry={sym:'$',name:'Gold',slot:'__gold__'};
  var all=[goldEntry].concat(p);
  showChooser('drop',all,'Drop what? (a-'+String.fromCharCode(97+all.length-1)+')');
}
function doDropMenu(){showDropChooser();}

// Drop handling is now integrated into handleChooserKey above

// Throw chooser: Gold + inventory
function showThrowChooser(){
  var d=lastDir;
  var goldEntry={sym:'$',name:'Gold',slot:'__throwgold__'};
  var all=[goldEntry].concat(applyState._inv||[]);
  chooserMode='throw';
  chooserItems=all;
  var html='<div style="color:#ffb000;margin-bottom:6px">Throw what? (a-'
    +String.fromCharCode(97+all.length-1)+')</div>';
  all.forEach(function(it,i){
    var letter=String.fromCharCode(97+i);
    html+='<div><span style="color:#88f">'+letter+')</span> '
      +esc(it.sym||'$')+' '+esc(it.name||it)+'</div>';
  });
  html+='<div style="margin-top:6px;color:#666">Esc to cancel</div>';
  chooserEl.innerHTML=html; chooserEl.style.display='block';
  document.getElementById('layout').focus();
}

// ── #layout keyboard handler ──────────────────────────────────────────────
document.getElementById('layout').addEventListener('keydown',function(e){
  if(IGNORE_KEYS.has(e.key))return;
  if(e.ctrlKey||e.altKey||e.metaKey)return;
  var key=KEY_MAP[e.key]||e.key;

  if(chooserMode){e.preventDefault();return;}
  if(invEl.style.display!=='none'){e.preventDefault();return;}
  if(lookEl.style.display!=='none'){e.preventDefault();return;}
  if(shopTabEl.style.display!=='none'){e.preventDefault();return;}

  if(confirmMode){
    e.preventDefault();
    if(key==='y'){
      if(confirmMode==='N'){confirmMode=null;gameActive=false;showNewGame();}
      else if(confirmMode==='Q'){confirmMode=null;sendCommand('quit_confirmed');}
    } else {confirmMode=null;document.getElementById('message-bar').textContent='OK, continuing.';}
    return;
  }

  if(!gameActive){if(key==='N'||key==='n')showNewGame();return;}
  e.preventDefault();

  if(key==='N'){confirmMode='N';sendCommand('confirm_new');return;}
  if(key==='Q'){confirmMode='Q';sendCommand('confirm_quit');return;}
  if(key==='A'){autoPickup=!autoPickup;document.getElementById('message-bar').textContent='Auto-pickup '+(autoPickup?'ON':'OFF');return;}
  if(key==='w'){doWield();return;}
  if(key==='W'){doWear();return;}
  if(key==='T'){doTakeoff();return;}
  if(key==='e'){doEat();return;}
  if(key==='i'||key==='I'){showInvOverlay();return;}
  if(key==='P'){doPutRing();return;}
  if(key==='R'){doRemoveRing();return;}
  if(key==='p'){sendCommand('p');return;}
  if(key===':'){sendCommand(':');return;}
  if(key==='d'){doDropMenu();return;}
  if(key==='t'){
    // Open the throw chooser regardless of direction.
    // Direction is validated when the item is selected in handleChooserKey.
    showThrowChooser();
    return;
  }
  if(key===','){sendCommand(',');return;}
  if(DIR_DELTA[key]){lastDir=DIR_DELTA[key];sendCommand(key);if(autoPickup)sendCommand(',');return;}
  sendCommand(key);
},true);

// ── Touch d-pad ───────────────────────────────────────────────────────────
document.querySelectorAll('.dpad[data-cmd]').forEach(function(btn){
  btn.addEventListener('click',function(){sendCommand(btn.dataset.cmd);});
  btn.addEventListener('touchend',function(e){e.preventDefault();sendCommand(btn.dataset.cmd);},{passive:false});
});

// ── New-game UI ───────────────────────────────────────────────────────────
function showNewGame(){
  var ni=document.getElementById('input-name');
  ni.value='';  // Wipe name field on every startup
  document.getElementById('new-game-screen').style.display='flex';
  ni.focus();
}

// Player name: exactly 3 alphanumeric characters.
// The field used to accept any printable ASCII, which let quotes, angle
// brackets and slashes reach the score store and the score board — a stored
// XSS and JSON-injection vector. The server enforces the same rule; this is
// only the convenience half, so a player is told before they submit.
var NAME_RE=/^[A-Za-z0-9]{3}$/;
var nameInput=document.getElementById('input-name');
nameInput.addEventListener('input',function(){
  var clean=nameInput.value.replace(/[^A-Za-z0-9]/g,'').slice(0,3);
  if(nameInput.value!==clean)nameInput.value=clean;
});
nameInput.addEventListener('keypress',function(e){
  var ch=e.key;
  if(ch&&ch.length===1&&!/[A-Za-z0-9]/.test(ch))e.preventDefault();
  if(nameInput.value.length>=3&&ch&&ch.length===1)e.preventDefault();
});

// Seed field: digits only, with an optional leading minus.
var seedInput=document.getElementById('input-seed');
if(seedInput){
  seedInput.addEventListener('input',function(){
    var clean=seedInput.value.replace(/[^0-9-]/g,'');
    clean=clean.charAt(0)==='-'?('-'+clean.slice(1).replace(/-/g,'')):clean.replace(/-/g,'');
    if(seedInput.value!==clean)seedInput.value=clean;
  });
  seedInput.addEventListener('keydown',function(e){if(e.key==='Enter')document.getElementById('btn-start').click();});
}

document.getElementById('btn-start').addEventListener('click',function(){
  var nameVal=nameInput.value.trim();
  if(!NAME_RE.test(nameVal)){nameInput.style.outline='2px solid #f33';nameInput.focus();
    document.getElementById('message-bar').textContent=
      'Name must be exactly 3 letters or digits.';
    setTimeout(function(){nameInput.style.outline='';},1500);return;}
  var seedVal=seedInput?seedInput.value.trim():'';
  startGame(nameVal,document.getElementById('input-role').value,seedVal);
});
nameInput.addEventListener('keydown',function(e){if(e.key==='Enter')document.getElementById('btn-start').click();});
document.getElementById('btn-newgame').addEventListener('click',function(){
  document.getElementById('gameover-screen').style.display='none';showNewGame();
});

// ── PWA ───────────────────────────────────────────────────────────────────
var dip=null;
window.addEventListener('beforeinstallprompt',function(e){e.preventDefault();dip=e;document.getElementById('pwa-banner').classList.add('visible');});
document.getElementById('btn-install').addEventListener('click',async function(){if(!dip)return;dip.prompt();await dip.userChoice;dip=null;document.getElementById('pwa-banner').classList.remove('visible');});
document.getElementById('btn-dismiss-install').addEventListener('click',function(){document.getElementById('pwa-banner').classList.remove('visible');});
window.addEventListener('appinstalled',function(){document.getElementById('pwa-banner').classList.remove('visible');dip=null;});

// ── Service worker ────────────────────────────────────────────────────────
if('serviceWorker'in navigator){window.addEventListener('load',function(){
  navigator.serviceWorker.register('/sw.js').then(function(r){setInterval(function(){r.update();},60000);}).catch(function(){});
});}

// ── Utilities ─────────────────────────────────────────────────────────────
function esc(s){return(s||'').replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;');}
function showError(m){document.getElementById('message-bar').textContent='\u26A0 '+m;}

setOnlineStatus(navigator.onLine);
document.getElementById('layout').focus();
