const RISK_COLORS = {
  'BUENO': '#22c55e',
  'ACEPTABLE': '#eab308',
  'MALO': '#f97316',
  'MUY MALO': '#ef4444',
  'EXTREMADAMENTE MALO': '#7e0023'
};

const MAX_POINTS = 20;
const trendData = { labels: [], co2: [], pm25: [], pm10: [] };
let nodoSeleccionado = 'general';
let vistaActual = 'live';

const crosshairPlugin = {
  id: 'crosshair',
  afterDraw(chartInstance) {
    const tooltip = chartInstance.tooltip;
    if (!tooltip || !tooltip.opacity) return;
    const activePoint = tooltip.getActiveElements()[0];
    if (!activePoint) return;
    const x = activePoint.element.x;
    const { top, bottom } = chartInstance.chartArea;
    const ctx = chartInstance.ctx;
    ctx.save();
    ctx.beginPath();
    ctx.moveTo(x, top);
    ctx.lineTo(x, bottom);
    ctx.lineWidth = 1;
    ctx.strokeStyle = '#8a93ab66';
    ctx.stroke();
    ctx.restore();
  }
};
Chart.register(crosshairPlugin);

function opcionesBase() {
  return {
    responsive: true,
    maintainAspectRatio: false,
    interaction: { mode: 'index', intersect: false },
    plugins: {
      legend: { display: false },
      tooltip: {
        enabled: true,
        backgroundColor: '#1a2338',
        titleColor: '#e8ecf5',
        titleFont: { size: 12, weight: '600' },
        bodyColor: '#e8ecf5',
        bodyFont: { size: 13 },
        padding: 10,
        cornerRadius: 6,
        displayColors: false,
        borderColor: '#232e48',
        borderWidth: 1
      }
    },
    scales: {
      y: { grid: { color: '#232e48' }, ticks: { color: '#8a93ab', font: { size: 11 } } },
      x: { grid: { display: false }, ticks: { color: '#8a93ab', font: { size: 11 }, maxTicksLimit: 8 } }
    }
  };
}

function datasetLinea(label, color) {
  return {
    label, data: [], borderColor: color, backgroundColor: 'transparent',
    borderWidth: 2, tension: 0.3, pointRadius: 0,
    pointHoverRadius: 4, pointHoverBackgroundColor: color,
    pointHoverBorderColor: '#0b0f19', pointHoverBorderWidth: 2
  };
}

const chartCo2 = new Chart(document.getElementById('trendChartCo2'), {
  type: 'line', data: { labels: trendData.labels, datasets: [datasetLinea('CO2', '#2a78d6')] }, options: opcionesBase()
});
const chartPm = new Chart(document.getElementById('trendChartPm'), {
  type: 'line', data: { labels: trendData.labels, datasets: [datasetLinea('PM2.5', '#eb6834'), datasetLinea('PM10', '#1baf7a')] }, options: opcionesBase()
});
const historicoChartCo2 = new Chart(document.getElementById('historicoChartCo2'), {
  type: 'line', data: { labels: [], datasets: [datasetLinea('CO2', '#2a78d6')] }, options: opcionesBase()
});
const historicoChartPm = new Chart(document.getElementById('historicoChartPm'), {
  type: 'line', data: { labels: [], datasets: [datasetLinea('PM2.5', '#eb6834'), datasetLinea('PM10', '#1baf7a')] }, options: opcionesBase()
});

function actualizarTarjetas(lectura) {
  if (!lectura) return;
  document.querySelectorAll('.stat-card .value')[0].innerHTML = `${lectura.co2Ppm ?? '—'} <span class="unit">ppm</span>`;
  document.querySelectorAll('.stat-card .value')[1].innerHTML = `${lectura.pm25Ugm3 ?? '—'} <span class="unit">µg/m³</span>`;
  document.querySelectorAll('.stat-card .value')[2].innerHTML = `${lectura.pm10Ugm3 ?? '—'} <span class="unit">µg/m³</span>`;

  const badge = document.getElementById('riskBadge');
  const color = RISK_COLORS[lectura.nivelRiesgo] || '#8a93ab';
  badge.textContent = lectura.nivelRiesgo || '—';
  badge.style.background = color + '22';
  badge.style.color = color;
}

function agregarLectura(lectura) {
  if (vistaActual !== 'live') return;
  if (nodoSeleccionado !== 'general' && lectura.nodoId !== nodoSeleccionado) return;

  const hora = new Date(lectura.timestampOrigen || Date.now()).toLocaleTimeString('es-MX', { hour: '2-digit', minute: '2-digit' });
  trendData.labels.push(hora);
  trendData.co2.push(lectura.co2Ppm);
  trendData.pm25.push(lectura.pm25Ugm3);
  trendData.pm10.push(lectura.pm10Ugm3);
  if (trendData.labels.length > MAX_POINTS) {
    trendData.labels.shift(); trendData.co2.shift(); trendData.pm25.shift(); trendData.pm10.shift();
  }
  chartCo2.data.datasets[0].data = trendData.co2;
  chartPm.data.datasets[0].data = trendData.pm25;
  chartPm.data.datasets[1].data = trendData.pm10;
  chartCo2.update('none');
  chartPm.update('none');
  actualizarTarjetas(lectura);
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

function mostrarVistaViva() {
  vistaActual = 'live';
  document.getElementById('livePanel').style.display = '';
  document.getElementById('historicoPanel').style.display = 'none';
}

function mostrarVistaHistorico() {
  vistaActual = 'historico';
  document.getElementById('livePanel').style.display = 'none';
  document.getElementById('historicoPanel').style.display = '';
  cargarHistoricoConFiltros();
}

function seleccionarNodo(nodoId, etiqueta) {
  mostrarVistaViva();
  nodoSeleccionado = nodoId;
  document.getElementById('filtroActualCo2').textContent = etiqueta;
  document.getElementById('filtroActualPm').textContent = etiqueta;

  document.querySelectorAll('#nodesList .list-item').forEach(el => {
    el.classList.toggle('selected', el.dataset.nodoId === nodoId);
  });

  trendData.labels.length = 0; trendData.co2.length = 0; trendData.pm25.length = 0; trendData.pm10.length = 0;
  chartCo2.update('none'); chartPm.update('none');

  document.querySelectorAll('.stat-card .value')[0].innerHTML = `— <span class="unit">ppm</span>`;
  document.querySelectorAll('.stat-card .value')[1].innerHTML = `— <span class="unit">µg/m³</span>`;
  document.querySelectorAll('.stat-card .value')[2].innerHTML = `— <span class="unit">µg/m³</span>`;
  document.getElementById('riskBadge').textContent = '—';
  document.getElementById('riskBadge').style.background = 'transparent';

  const url = nodoId === 'general'
    ? '/api/v1/lecturas/historico?horas=24'
    : `/api/v1/lecturas/historico?nodoId=${nodoId}&horas=24`;

  fetch(url)
    .then(r => {
      if (!r.ok) throw new Error(`El servidor respondió ${r.status} al pedir el histórico de este nodo`);
      return r.json();
    })
    .then(datos => {
      datos.slice(-MAX_POINTS).forEach(d => {
        trendData.labels.push(new Date(d.timestampOrigen).toLocaleTimeString('es-MX', { hour: '2-digit', minute: '2-digit' }));
        trendData.co2.push(d.co2Ppm); trendData.pm25.push(d.pm25Ugm3); trendData.pm10.push(d.pm10Ugm3);
      });
      chartCo2.data.datasets[0].data = trendData.co2;
      chartPm.data.datasets[0].data = trendData.pm25;
      chartPm.data.datasets[1].data = trendData.pm10;
      chartCo2.update('none'); chartPm.update('none');
      if (datos.length) actualizarTarjetas(datos[datos.length - 1]);
    })
    .catch(err => console.error('No se pudo cargar el histórico del nodo:', err));
}

function seleccionarHistorico() {
  document.querySelectorAll('#nodesList .list-item').forEach(el => {
    el.classList.toggle('selected', el.dataset.nodoId === 'historico');
  });
  mostrarVistaHistorico();
}

function actualizarResumenHistorico(datos) {
  const co2Vals = datos.map(d => d.co2Ppm).filter(v => v != null);
  const pm25Vals = datos.map(d => d.pm25Ugm3).filter(v => v != null);
  const pm10Vals = datos.map(d => d.pm10Ugm3).filter(v => v != null);
  const promedio = (arr) => arr.length ? (arr.reduce((a, b) => a + b, 0) / arr.length).toFixed(1) : '—';

  document.getElementById('histCo2Avg').innerHTML = `${promedio(co2Vals)} <span class="unit">ppm</span>`;
  document.getElementById('histPm25Avg').innerHTML = `${promedio(pm25Vals)} <span class="unit">µg/m³</span>`;
  document.getElementById('histPm10Avg').innerHTML = `${promedio(pm10Vals)} <span class="unit">µg/m³</span>`;

  const PRIORIDAD_RIESGO = { 'BUENO': 0, 'ACEPTABLE': 1, 'MALO': 2, 'MUY MALO': 3, 'EXTREMADAMENTE MALO': 4 };
  let peorNivel = null;
  datos.forEach(d => {
    if (d.nivelRiesgo && (peorNivel === null || PRIORIDAD_RIESGO[d.nivelRiesgo] > PRIORIDAD_RIESGO[peorNivel])) {
      peorNivel = d.nivelRiesgo;
    }
  });

  const badge = document.getElementById('histRiskBadge');
  if (peorNivel) {
    const color = RISK_COLORS[peorNivel] || '#8a93ab';
    badge.textContent = peorNivel;
    badge.style.background = color + '22';
    badge.style.color = color;
  } else {
    badge.textContent = 'Sin datos';
    badge.style.background = 'transparent';
    badge.style.color = 'var(--text-secondary)';
  }
}

function cargarHistoricoConFiltros() {
  const ubicacion = document.getElementById('filtroUbicacion').value;
  const periodo = document.getElementById('filtroPeriodo').value;
  const params = new URLSearchParams();
  if (ubicacion !== 'general') params.set('nodoId', ubicacion);

  if (periodo === 'personalizado') {
    const desde = document.getElementById('fechaDesde').value;
    const hasta = document.getElementById('fechaHasta').value;
    if (!desde || !hasta) return;
    params.set('desde', desde); params.set('hasta', hasta);
  } else {
    const horasPorPeriodo = { '24h': 24, 'semana': 168, 'mes': 720 };
    params.set('horas', horasPorPeriodo[periodo] || 24);
  }

  fetch(`/api/v1/lecturas/historico?${params.toString()}`)
    .then(r => {
      if (!r.ok) throw new Error(`El servidor respondió ${r.status} al pedir el histórico`);
      return r.json();
    })
    .then(datos => {
      const labels = datos.map(d => new Date(d.timestampOrigen).toLocaleString('es-MX', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' }));
      historicoChartCo2.data.labels = labels;
      historicoChartCo2.data.datasets[0].data = datos.map(d => d.co2Ppm);
      historicoChartPm.data.labels = labels;
      historicoChartPm.data.datasets[0].data = datos.map(d => d.pm25Ugm3);
      historicoChartPm.data.datasets[1].data = datos.map(d => d.pm10Ugm3);
      historicoChartCo2.update(); historicoChartPm.update();
      actualizarResumenHistorico(datos);
    })
    .catch(err => console.error('No se pudo cargar el histórico:', err));
}

function configurarLimitesFecha() {
  fetch('/api/v1/lecturas/primera')
    .then(r => r.json())
    .then(datos => {
      const hoy = new Date().toISOString().split('T')[0];
      const desdeInput = document.getElementById('fechaDesde');
      const hastaInput = document.getElementById('fechaHasta');
      hastaInput.max = hoy;
      desdeInput.max = hoy;
      if (datos.timestampOrigen) {
        const minFecha = datos.timestampOrigen.split('T')[0];
        desdeInput.min = minFecha;
        hastaInput.min = minFecha;
      }
    })
    .catch(() => {});
}

function clamparFecha(input) {
  if (input.min && input.value < input.min) input.value = input.min;
  if (input.max && input.value > input.max) input.value = input.max;
}

document.getElementById('filtroPeriodo').addEventListener('change', (e) => {
  const esPersonalizado = e.target.value === 'personalizado';
  document.getElementById('campoDesde').style.display = esPersonalizado ? '' : 'none';
  document.getElementById('campoHasta').style.display = esPersonalizado ? '' : 'none';
  if (esPersonalizado) configurarLimitesFecha();
  else cargarHistoricoConFiltros();
});
document.getElementById('filtroUbicacion').addEventListener('change', cargarHistoricoConFiltros);

document.getElementById('fechaDesde').addEventListener('change', (e) => {
  clamparFecha(e.target);
  const hastaInput = document.getElementById('fechaHasta');
  hastaInput.min = e.target.value;
  if (hastaInput.value && hastaInput.value < e.target.value) hastaInput.value = e.target.value;
  cargarHistoricoConFiltros();
});

document.getElementById('fechaHasta').addEventListener('change', (e) => {
  clamparFecha(e.target);
  const desdeInput = document.getElementById('fechaDesde');
  if (desdeInput.value && e.target.value < desdeInput.value) e.target.value = desdeInput.value;
  cargarHistoricoConFiltros();
});

document.getElementById('btnSaberMas').addEventListener('click', () => {
  const breve = document.getElementById('infoBreve');
  const detalle = document.getElementById('infoDetalle');
  const boton = document.getElementById('btnSaberMas');
  const tarjeta = boton.closest('.card');
  const expandido = detalle.style.display !== 'none';

  breve.style.display = expandido ? '' : 'none';
  detalle.style.display = expandido ? 'none' : '';
  boton.textContent = expandido ? 'Saber más' : 'Ver menos';

  tarjeta.appendChild(boton);
});

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
      general.innerHTML = `<span class="dot" style="background:var(--accent)"></span><div class="text"><div class="title">General</div><div class="meta">Todos los nodos</div></div>`;
      general.addEventListener('click', () => seleccionarNodo('general', 'general'));
      list.appendChild(general);

      const historico = document.createElement('div');
      historico.className = 'list-item clickable separator';
      historico.dataset.nodoId = 'historico';
      historico.innerHTML = `<span class="dot" style="background:var(--text-secondary)"></span><div class="text"><div class="title">Histórico</div><div class="meta">Consultar por rango de fechas</div></div>`;
      historico.addEventListener('click', seleccionarHistorico);
      list.appendChild(historico);

      const selectUbicacion = document.getElementById('filtroUbicacion');
      selectUbicacion.innerHTML = '<option value="general">Todos</option>';

      nodos.forEach(n => {
        const item = document.createElement('div');
        item.className = 'list-item clickable';
        item.dataset.nodoId = n.id;
        item.innerHTML = `<span class="dot" style="background:${n.estatus ? '#22c55e' : '#ef4444'}"></span><div class="text"><div class="title">${n.ubicacion || 'Sin asignar'}</div><div class="meta">${n.id.toString().slice(0, 8)}</div></div>`;
        item.addEventListener('click', () => seleccionarNodo(n.id, n.ubicacion || n.id.toString().slice(0, 8)));
        list.appendChild(item);

        const opcion = document.createElement('option');
        opcion.value = n.id;
        opcion.textContent = n.ubicacion || n.id.toString().slice(0, 8);
        selectUbicacion.appendChild(opcion);
      });
    })
    .catch(() => { document.getElementById('nodesList').innerHTML = '<div class="empty">No se pudo cargar</div>'; });
}

conectar();
cargarNodos();
seleccionarNodo('general', 'general');