class _TagCamel_ extends HTMLElement {
  constructor() {
	super();
	this._propertyUpdatedFromServer = {};
	this.$ = {};
	this.initDefaults();
	var shadow = this.attachShadow( { mode: 'open' } );
	var style = document.createElement("style");
    style.innerHTML = `
      :host {
        display: inline-block;
      }
    `;
	shadow.appendChild(style);
	shadow.appendChild(document.createElement("slot"));
  }

  _PropertyMethods_

  initDefaults() {
	  _PropertyDefaults_
  }

  _sync(property, newValue) {
    if (this.$server) {
      if (!this._propertyUpdatedFromServer[property]) {
        this.$server.sync(property, newValue);
      } else {
        this._propertyUpdatedFromServer[property] = false;
      }
    }
  }

  _updatePropertyFromServer(property, newValue) {
    if (this.__proto__.hasOwnProperty(property)) {
      this._propertyUpdatedFromServer[property] = true;
      this[property] = newValue;
    }
  }

  connectedCallback() {
    if (super.connectedCallback) {
	  super.connectedCallback();
    }


    if (_TagCamel_.rootId != null){
        this._connect(_TagCamel_.rootId);
    }
    else {
        var self = this;
        document.body.addEventListener('root-element', function(event) {
            self._connect(event.detail);
        });
    }
  }

  _connect(rootId){
      var flowRoot;
      if ( rootId ){
          flowRoot = document.getElementById(rootId);
      }
      else {
          flowRoot = document.body;
      }
      if (!this.$.id) {
        this._registerElement(flowRoot);
      } else if (flowRoot && flowRoot.$server) {
          this.$server.reconnect();
      }
      console.debug('connected', this);
  }

  _registerElement(flowRoot) {
    this.$.id = "_TagCamel_-" + _TagCamel_.id++;

    // Needed to make Flow do lookup correctly
    const poller = () => {
      if (flowRoot && flowRoot.$server) {
        flowRoot.$ = flowRoot.$ || {};
        flowRoot.$[this.$.id] = this;
        flowRoot.$server.connectWebComponent('_TagDash_', this.$.id);
      } else {
        setTimeout(poller, 10);
      }
    };

    poller();
  }

  disconnectedCallback() {
    this.$server.disconnected();

    console.log('disconnected', this);
  }

  serverConnected() {
	_PropertySync_
  }
}

_TagCamel_.rootId = null;
_TagCamel_.id = 0;

function addRootElementEventListener(){
    document.body.addEventListener('root-element', function(event) {
        _TagCamel_.rootId = event.detail;
    });
}
if ( document.body ){
    addRootElementEventListener();
}
else {
    window.addEventListener('load', function () {
        addRootElementEventListener();
    });
}

customElements.define('_TagDash_', _TagCamel_);
