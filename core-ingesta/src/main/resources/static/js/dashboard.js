const RISK_COLORS = {
  'BUENO': '#22c55e',
  'ACEPTABLE': '#eab308',
  'MALO': '#f97316',
  'MUY MALO': '#ef4444',
  'EXTREMADAMENTE MALO': '#7e0023'
};

let nodoSeleccionado = 'general';
const MAX_POINTS = 20;
const trendData = { labels: [], co2: [], pm25: [], pm10: [] };

const chart = new Chart(document.getElementById('trendChart'), {
  type: 'line',
  data: {
    labels: trendData.labels,
    datasets: [
      { label: 'CO2', data: trendData.co2, borderColor: '#2a78d6', backgroundColor: 'transparent', borderWidth: 2, tension: 0.3, pointRadius: 0 },
      { label: 'PM2.5', data: trendData.pm25, borderColor: '#eb6834', backgroundColor: 'transparent', borderWidth: 2, tension: 0.3, pointRadius: 0 },
      { label: 'PM10', data: trendData.pm10, borderColor: '#1baf7a', backgroundColor: 'transparent', borderWidth: 2, tension: 0.3, pointRadius: 0 }
    ]
  },
  options: {
    responsive: true,
    maintainAspectRatio: false,
    plugins: { legend: { display: false } },
    scales: {
      y: { grid: { color: '#232e48' }, ticks: { color: '#8a93ab', font: { size: 11 } } },
      x: { grid: { display: false }, ticks: { color: '#8a93ab', font: { size: 11 } } }
    }
  }
});

function agregarLectura(lectura) {
  if (nodoSeleccionado !== 'general' && lectura.nodoId !== nodoSeleccionado) return;
  const hora = new Date(lectura.timestampOrigen || Date.now()).toLocaleTimeString('es-MX', { hour: '2-digit', minute: '2-digit' });
  trendData.labels.push(hora);
  trendData.co2.push(lectura.co2Ppm);
  trendData.pm25.push(lectura.pm25Ugm3);
  trendData.pm10.push(lectura.pm10Ugm3);
  if (trendData.labels.length > MAX_POINTS) {
    trendData.labels.shift(); trendData.co2.shift(); trendData.pm25.shift(); trendData.pm10.shift();
  }
  chart.update('none');

  document.querySelectorAll('.stat-card .value')[0].innerHTML = `${lectura.co2Ppm ?? '—'} <span class="unit">ppm</span>`;
  document.querySelectorAll('.stat-card .value')[1].innerHTML = `${lectura.pm25Ugm3 ?? '—'} <span class="unit">µg/m³</span>`;
  document.querySelectorAll('.stat-card .value')[2].innerHTML = `${lectura.pm10Ugm3 ?? '—'} <span class="unit">µg/m³</span>`;

  const badge = document.getElementById('riskBadge');
  const color = RISK_COLORS[lectura.nivelRiesgo] || '#8a93ab';
  badge.textContent = lectura.nivelRiesgo || '—';
  badge.style.background = color + '22';
  badge.style.color = color;
}

function agregarAlerta(alerta) {
  const list = document.getElementById('alertsList');
  if (list.querySelector('.empty')) list.innerHTML = '';
  const color = alerta.tipoAlerta && alerta.tipoAlerta.includes('MUY_MALO') ? RISK_COLORS['MUY MALO'] : '#ef4444';
  const item = document.createElement('div');
  item.className = 'list-item';
  item.innerHTML = `
    <span class="dot" style="background:${color}"></span>
    <div class="text">
      <div class="title">${alerta.tipoAlerta} — nodo ${(alerta.nodoId || '').toString().slice(0, 8)}</div>
      <div class="meta">${new Date(alerta.timestampOrigen || Date.now()).toLocaleTimeString('es-MX')}</div>
    </div>`;
  list.prepend(item);
  while (list.children.length > 8) list.removeChild(list.lastChild);
}

function conectar() {
  const socket = new SockJS('/ws-calidad-aire');
  const stompClient = Stomp.over(socket);
  stompClient.debug = null;

  stompClient.connect({}, () => {
    document.getElementById('connDot').classList.remove('offline');
    document.getElementById('connLabel').textContent = 'Conectado';

    stompClient.subscribe('/topic/lecturas', (msg) => agregarLectura(JSON.parse(msg.body)));
    stompClient.subscribe('/topic/alertas', (msg) => agregarAlerta(JSON.parse(msg.body)));
  }, () => {
    document.getElementById('connDot').classList.add('offline');
    document.getElementById('connLabel').textContent = 'Desconectado — reintentando…';
    setTimeout(conectar, 3000);
  });
}

function cargarNodos() {
  fetch('/api/v1/nodos')
    .then(r => r.json())
    .then(nodos => {
      const list = document.getElementById('nodesList');
      list.innerHTML = '';

      const general = document.createElement('div');
      general.className = 'list-item clickable selected';
      general.dataset.nodoId = 'general';
      general.innerHTML = `
        <span class="dot" style="background:var(--accent)"></span>
        <div class="text">
          <div class="title">General</div>
          <div class="meta">Todos los nodos</div>
        </div>`;
      general.addEventListener('click', () => seleccionarNodo('general', 'general'));
      list.appendChild(general);

      nodos.forEach(n => {
        const item = document.createElement('div');
        item.className = 'list-item clickable';
        item.dataset.nodoId = n.id;
        item.innerHTML = `
          <span class="dot" style="background:${n.estatus ? '#22c55e' : '#ef4444'}"></span>
          <div class="text">
            <div class="title">${n.ubicacion || 'Sin asignar'}</div>
            <div class="meta">${n.id.toString().slice(0, 8)}</div>
          </div>`;
        item.addEventListener('click', () => seleccionarNodo(n.id, n.ubicacion || n.id.toString().slice(0, 8)));
        list.appendChild(item);
      });
    })
    .catch(() => { document.getElementById('nodesList').innerHTML = '<div class="empty">No se pudo cargar</div>'; });
}

function seleccionarNodo(nodoId, etiqueta) {
  nodoSeleccionado = nodoId;
  document.getElementById('filtroActual').textContent = etiqueta;

  document.querySelectorAll('#nodesList .list-item').forEach(el => {
    el.classList.toggle('selected', el.dataset.nodoId === nodoId);
  });

  trendData.labels.length = 0;
  trendData.co2.length = 0;
  trendData.pm25.length = 0;
  trendData.pm10.length = 0;
  chart.update('none');

  document.querySelectorAll('.stat-card .value')[0].innerHTML = `— <span class="unit">ppm</span>`;
  document.querySelectorAll('.stat-card .value')[1].innerHTML = `— <span class="unit">µg/m³</span>`;
  document.querySelectorAll('.stat-card .value')[2].innerHTML = `— <span class="unit">µg/m³</span>`;
  document.getElementById('riskBadge').textContent = '—';
  document.getElementById('riskBadge').style.background = 'transparent';
  document.getElementById('filtroHistorico').textContent = etiqueta;
  cargarHistorico(nodoId);

  if (nodoId !== 'general') {
    fetch(`/api/v1/lecturas/ultima/${nodoId}`)
      .then(r => r.ok ? r.json() : null)
      .then(lectura => { if (lectura) agregarLectura(lectura); });
  }
}



const historicoChart = new Chart(document.getElementById('historicoChart'), {
  type: 'line',
  data: {
    labels: [],
    datasets: [
      { label: 'CO2', data: [], borderColor: '#2a78d6', backgroundColor: 'transparent', borderWidth: 2, tension: 0.2, pointRadius: 0 },
      { label: 'PM2.5', data: [], borderColor: '#eb6834', backgroundColor: 'transparent', borderWidth: 2, tension: 0.2, pointRadius: 0 },
      { label: 'PM10', data: [], borderColor: '#1baf7a', backgroundColor: 'transparent', borderWidth: 2, tension: 0.2, pointRadius: 0 }
    ]
  },
  options: {
    responsive: true,
    maintainAspectRatio: false,
    plugins: { legend: { display: false } },
    scales: {
      y: { grid: { color: '#232e48' }, ticks: { color: '#8a93ab', font: { size: 11 } } },
      x: { grid: { display: false }, ticks: { color: '#8a93ab', font: { size: 11 }, maxTicksLimit: 8 } }
    }
  }
});

function cargarHistorico(nodoId) {
  const url = (nodoId && nodoId !== 'general')
    ? `/api/v1/lecturas/historico?nodoId=${nodoId}`
    : `/api/v1/lecturas/historico`;

  fetch(url)
    .then(r => r.json())
    .then(datos => {
      historicoChart.data.labels = datos.map(d => new Date(d.timestampOrigen).toLocaleString('es-MX', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' }));
      historicoChart.data.datasets[0].data = datos.map(d => d.co2Ppm);
      historicoChart.data.datasets[1].data = datos.map(d => d.pm25Ugm3);
      historicoChart.data.datasets[2].data = datos.map(d => d.pm10Ugm3);
      historicoChart.update();
    })
    .catch(() => {});
}


cargarHistorico('general');
conectar();
cargarNodos();