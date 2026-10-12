const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');

function fixture() {
    let now = 0, serial = 0;
    const timers = new Map(), input = new EventTarget(), calls = [], invalidations = [];
    input.value = '';
    const window = {}, document = {addEventListener() {}};
    vm.runInNewContext(fs.readFileSync(path.resolve(__dirname, '../../main/resources/static/js/live-search.js'), 'utf8'), {
        window, document,
        setTimeout(fn, delay) {const id = ++serial; timers.set(id, {fn, at:now + delay}); return id;},
        clearTimeout(id) {timers.delete(id);}
    });
    const binding = window.AppSearch.bind(input, () => calls.push({value:input.value, at:now}), () => invalidations.push(now));
    const emit = (type, props = {}) => {const event = new Event(type, {cancelable:true}); Object.assign(event, props); input.dispatchEvent(event); return event;};
    const type = value => {input.value = value; emit('input');};
    const advance = amount => {
        const end = now + amount;
        while (true) {
            const next = [...timers].filter(([,t]) => t.at <= end).sort((a,b) => a[1].at-b[1].at)[0];
            if (!next) break;
            now = next[1].at; timers.delete(next[0]); next[1].fn();
        }
        now = end;
    };
    return {binding, calls, invalidations, emit, type, advance};
}

test('rapid typing sends only the final query, exactly 350ms after typing stops', () => {
    const f = fixture();
    f.type('i'); f.advance(150); f.type('in'); f.advance(150); f.type('interest');
    f.advance(349); assert.equal(f.calls.length, 0);
    f.advance(1); assert.deepEqual(f.calls, [{value:'interest', at:650}]);
    f.advance(1000); assert.equal(f.calls.length, 1);
});

test('blur flushes immediately and cancels the scheduled duplicate and Apply', () => {
    const f = fixture(); f.type('bank'); f.advance(40); f.emit('blur');
    assert.deepEqual(f.calls, [{value:'bank', at:40}]);
    f.binding.run(); f.advance(1000); assert.equal(f.calls.length, 1);
});

test('Enter searches immediately without submitting a surrounding financial form', () => {
    const f = fixture(); f.type('capital');
    assert.equal(f.emit('keydown', {key:'Enter'}).defaultPrevented, true);
    f.advance(350); assert.equal(f.calls.length, 1);
});

test('clearing a query refreshes the unfiltered list', () => {
    const f = fixture(); f.type('bank'); f.advance(350); f.type(''); f.advance(350);
    assert.deepEqual(f.calls.map(c => c.value), ['bank','']);
});

test('new input invalidates an outstanding response before its replacement request runs', () => {
    const f = fixture(); f.type('old'); f.advance(350);
    const before = f.invalidations.length;
    f.type('new'); assert.equal(f.invalidations.length, before + 1);
    assert.equal(f.calls.length, 1); f.advance(350); assert.equal(f.calls[1].value, 'new');
});

test('IME composition does not query or intercept Enter until the committed text is ready', () => {
    const f = fixture(); f.emit('compositionstart'); f.type('途中'); f.advance(500);
    assert.equal(f.emit('keydown', {key:'Enter', isComposing:true}).defaultPrevented, false);
    f.emit('blur'); assert.equal(f.calls.length, 0);
    f.emit('compositionend'); f.advance(350); assert.equal(f.calls.length, 1);
});

test('closing a picker cancels pending work; failed searches can be retried', () => {
    const f = fixture(); f.type('bank'); f.binding.cancel(); f.advance(1000);
    assert.equal(f.calls.length, 0);
    f.binding.run(); f.binding.cancel(); f.binding.run(); assert.equal(f.calls.length, 2);
});


test('GET registries keep filters, reset pagination and remember the typing position', () => {
    const input = new EventTarget(); Object.assign(input, {name:'search', type:'search', value:'bank', selectionStart:4, selectionEnd:4, hasAttribute:()=>false});
    const page = {value:'3'}, form = new EventTarget(), submissions = [];
    Object.assign(form, {method:'get', action:'http://localhost/finance/accounts', hasAttribute:()=>false,
        querySelector:()=>null, querySelectorAll:selector=>selector === 'input' ? [input] : [page], closest:()=>null,
        requestSubmit:()=>{submissions.push(page.value); form.dispatchEvent(new Event('submit',{cancelable:true}));}});
    let ready; const store = new Map(); const document = {activeElement:input, addEventListener:(type,fn)=>{if(type==='DOMContentLoaded') ready=fn;}, querySelectorAll:()=>[form]};
    const window = {addEventListener() {}}, sessionStorage = {getItem:k=>store.get(k)||null, removeItem:k=>store.delete(k), setItem:(k,v)=>store.set(k,v)};
    class Fields {constructor(){} forEach(fn){fn(input.value,'search'); fn('ASSET','type'); fn(page.value,'page');}}
    vm.runInNewContext(fs.readFileSync(path.resolve(__dirname, '../../main/resources/static/js/live-search.js'), 'utf8'), {
        window, document, sessionStorage, location:{href:'http://localhost/finance/accounts?page=3',origin:'http://localhost'},
        URL, FormData:Fields, setTimeout, clearTimeout
    });
    ready(); input.dispatchEvent(new Event('input')); input.dispatchEvent(new Event('blur'));
    assert.deepEqual(submissions, ['0']);
    const remembered = JSON.parse(store.get('app-search-focus'));
    assert.equal(remembered.url, 'http://localhost/finance/accounts?search=bank&type=ASSET&page=0');
    assert.equal(remembered.start, 4);
    const duplicate = new Event('submit', {cancelable:true}); form.dispatchEvent(duplicate); assert.equal(duplicate.defaultPrevented, true);
});

test('automatic registry search never submits POST forms or report download forms', () => {
    let ready, bindings = 0; const field = new EventTarget(); field.addEventListener = () => bindings++; Object.assign(field,{name:'search',type:'search',hasAttribute:()=>false});
    const post = {method:'post',hasAttribute:()=>false};
    const download = {method:'get',hasAttribute:()=>false,querySelectorAll:()=>[field],querySelector:()=>({name:'format'})};
    const document = {addEventListener:(type,fn)=>{ready=fn;},querySelectorAll:()=>[post,download]};
    const window = {};
    vm.runInNewContext(fs.readFileSync(path.resolve(__dirname, '../../main/resources/static/js/live-search.js'), 'utf8'), {window,document,setTimeout,clearTimeout});
    ready(); field.dispatchEvent(new Event('input')); field.dispatchEvent(new Event('blur'));
    assert.equal(bindings, 0);
});
