<?php

	function getDbFilePath() {
		//~ $ipAddress = gethostbyname($_SERVER['SERVER_NAME']);
		if (gethostname() === 'jetpack') {
		//~ if ($ipAddress=='192.168.242.142') {
			return './db/ar.db';
		} else {
			return './ar.db';
		}
	}

	function createTable($db, $tableName, $sqlDef) {
		$results = $db->query(sprintf("SELECT name FROM sqlite_master WHERE type='table' AND name='%s'", $tableName));
		if ($row = $results->fetchArray()) {
			// Table exists
			//~ printf("<p/>Table exists");
		} else {
			// Need to create the table
			//~ printf("<p/>Creating table %s", $tableName);
			if (!($db->query(sprintf("CREATE TABLE %s %s", $tableName, $sqlDef)))) {
				// Table creation failed
			} else {
				// Success
			}
		}
	}

	function createAllTables($db) {
		createTable($db, "phones", "
			(id integer PRIMARY KEY AUTOINCREMENT, 
			os_codename		TEXT, 
			os_release	 	TEXT, 
			os_increment 	TEXT, 
			device		 	TEXT, 
			model		 	TEXT, 
			product		 	TEXT,
			manufacturer 	TEXT,
			brand		 	TEXT
			)"
			);
		createTable($db, "phone_cameras", "
			(id integer PRIMARY KEY AUTOINCREMENT, 
			phone_id		INTEGER, 
			facing_id		INTEGER, 
			facing_string	TEXT)"
			);
		createTable($db, "picsizes", "
			(id integer PRIMARY KEY AUTOINCREMENT, 
			w		INTEGER, 
			h		INTEGER)"
			);
		createTable($db, "picformats", "
			(id integer PRIMARY KEY, 
			name		TEXT)"
			);
		createTable($db, "preview_sizes", "
			(camera_id integer,
			picsize_id integer, 
			PRIMARY KEY (camera_id, picsize_id) )"
			);
		createTable($db, "picture_sizes", "
			(camera_id integer, 
			picsize_id integer, 
			PRIMARY KEY (camera_id, picsize_id) )"
			);
		createTable($db, "preview_formats", "
			(camera_id integer, 
			format_id integer, 
			PRIMARY KEY (camera_id, format_id) )"
			);
		createTable($db, "picture_formats", "
			(camera_id integer, 
			format_id integer, 
			PRIMARY KEY (camera_id, format_id) )"
			);
	}

	function tableColumnExists($db, $tableName, $columnName) {
		$results = $db->query(sprintf('pragma table_info(%s)', $tableName));
		while ($row = $results->fetchArray(SQLITE3_NUM)) {
			if (strcasecmp($row[1], $columnName)===0) {
				return true;
			}
		}
		return false;
	}

	function ensureDbUpToDate($db) {
		createAllTables($db);
		if (! tableColumnExists($db, 'phones', 'manufacturer'))
			$db->query("ALTER TABLE phones ADD manufacturer TEXT");
		if (! tableColumnExists($db, 'phones', 'brand'))
			$db->query("ALTER TABLE phones ADD brand TEXT");
	}
	
	function saveNewPhoneInfo($db,$os_codename,$os_release,$os_increment,$device,$model,$product, $manufacturer, $brand) {
		$stmt = $db->prepare('INSERT INTO phones
			(os_codename		
			,os_release	 	
			,os_increment 	
			,device		 	
			,model		 	
			,product
			,manufacturer
			,brand
			)
		VALUES
			(:os_codename
			,:os_release
			,:os_increment
			,:device
			,:model
			,:product
			,:manufacturer
			,:brand
			)');
		if ($stmt===false) {
			// Prepare failed
			return -1;
		} else {
			$stmt->bindValue(':os_codename', 	$os_codename, 	SQLITE3_TEXT);
			$stmt->bindValue(':os_release', 	$os_release, 	SQLITE3_TEXT);
			$stmt->bindValue(':os_increment', 	$os_increment,	SQLITE3_TEXT);
			$stmt->bindValue(':device', 		$device, 		SQLITE3_TEXT);
			$stmt->bindValue(':model', 			$model, 		SQLITE3_TEXT);
			$stmt->bindValue(':product', 		$product, 		SQLITE3_TEXT);
			$stmt->bindValue(':manufacturer', 	$manufacturer,	SQLITE3_TEXT);
			$stmt->bindValue(':brand', 			$brand, 		SQLITE3_TEXT);
			$res = $stmt->execute();
			if (! $res ) {
				// Insert failed
				$stmt->close();
				return -1;
			} else {
				$stmt->close();
				return $db->lastInsertRowid();
			}
		}			
	}

	function saveCameraInfo($db,$idPi,$facingId,$facingString) {
		$stmt = $db->prepare('INSERT INTO phone_cameras
			(phone_id		
			,facing_id		
			,facing_string)
			VALUES (
			:phone_id		
			,:facing_id		
			,:facing_string)');
		if ($stmt===false) {
			// Prepare failed
			return -1;
		} else {
			$stmt->bindValue(':phone_id', 		$idPi, 				SQLITE3_INTEGER);
			$stmt->bindValue(':facing_id', 		intval($facingId), 	SQLITE3_INTEGER);
			$stmt->bindValue(':facing_string', 	$facingString,		SQLITE3_TEXT);
			$res = $stmt->execute();
			if (! $res ) {
				// Insert failed
				$stmt->close();
				return -1;
			} else {
				$stmt->close();
				return $db->lastInsertRowid();
			}
		}
	}

	function findPicSizeId($db, $w, $h) {
		$stmt = $db->prepare('SELECT id FROM picsizes WHERE (w=:w) AND (h=:h)');
		$stmt->bindValue(':w', intval($w), SQLITE3_INTEGER);
		$stmt->bindValue(':h', intval($h), SQLITE3_INTEGER);
		$qr = $stmt->execute();
		if ( ($qr) && ($row = $qr->fetchArray())) {
			return $row[0];
		} else {
			return -1;
		}
	}

	function getPicSizeId($db, $w, $h) {
		if ( ($picSizeId = findPicSizeId($db, $w, $h)) >= 0) {
			return $picSizeId;
		} else {
			$stmt = $db->prepare('INSERT INTO picsizes (w,h) values (:w, :h)');
			$stmt->bindValue(':w', intval($w), SQLITE3_INTEGER);
			$stmt->bindValue(':h', intval($h), SQLITE3_INTEGER);
			if (! $stmt->execute() ) {
				// Insert failed
				return -1;
			} else {
				return $db->lastInsertRowid();
			}
		}
	}

	function saveCameraSize($db,$idCi,$tableName,$w,$h) {
		$sizeId = getPicSizeId($db, $w, $h);
		$stmt = $db->prepare(sprintf("INSERT INTO %s (camera_id, picsize_id) values (:camera_id, :picsize_id)", $tableName));
		if ($stmt===false) {
			// Prepare failed
			return -1;
		} else {
			$stmt->bindValue(':camera_id', 		$idCi, 		SQLITE3_INTEGER);
			$stmt->bindValue(':picsize_id', 	$sizeId, 	SQLITE3_INTEGER);
			$res = $stmt->execute();
			if (! $res ) {
				// Insert failed
				$stmt->close();
				return -1;
			} else {
				$stmt->close();
				return $db->lastInsertRowid();
			}
		}
	}
	
	function saveCameraPreviewSize($db,$idCi,$w,$h) {
		saveCameraSize($db,$idCi,"preview_sizes",$w,$h);
	}
	
	function saveCameraPictureSize($db,$idCi,$w,$h) {
		saveCameraSize($db,$idCi,"picture_sizes",$w,$h);
	}

	function getPicFormatId($db, $id, $name) {
		$stmt = $db->prepare('SELECT id FROM picformats WHERE (id=:id)');
		$stmt->bindValue(':id', intval($id), SQLITE3_INTEGER);
		$qr = $stmt->execute();
		if ( ($qr) && ($row = $qr->fetchArray())) {
			return $id;
		} else {
			$stmt = $db->prepare('INSERT INTO picformats (id,name) values (:id, :name)');
			$stmt->bindValue(':id', intval($id), SQLITE3_INTEGER);
			$stmt->bindValue(':name', $name, SQLITE3_INTEGER);
			if (! $stmt->execute() ) {
				// Insert failed
				return -1;
			} else {
				return $db->lastInsertRowid();
			}
		}
	}

	function saveCameraFormat($db,$idCi,$tableName,$w,$h) {
		$formatId = getPicFormatId($db, $w, $h);
		$stmt = $db->prepare(sprintf("INSERT INTO %s (camera_id, format_id) values (:camera_id, :format_id)", $tableName));
		if ($stmt===false) {
			// Prepare failed
			return -1;
		} else {
			$stmt->bindValue(':camera_id', 	$idCi, 		SQLITE3_INTEGER);
			$stmt->bindValue(':format_id', 	$formatId, 	SQLITE3_INTEGER);
			$res = $stmt->execute();
			if (! $res ) {
				// Insert failed
				$stmt->close();
				return -1;
			} else {
				$stmt->close();
				return $db->lastInsertRowid();
			}
		}
	}

	function saveCameraPreviewFormat($db,$idCi,$id,$name) {
		saveCameraFormat($db,$idCi,"preview_formats",$id,$name);
	}
	
	function saveCameraPictureFormat($db,$idCi,$id,$name) {
		saveCameraFormat($db,$idCi,"picture_formats",$id,$name);
	}

	function getQueryAsHtmlTableRows($db, $sql) {
		$results = $db->query($sql);
		$str = '';
		while ($row = $results->fetchArray(SQLITE3_ASSOC)) {
			$str .= '<tr>';
			foreach ($row as $d) {
				$str .= '<td>' . $d . '</td>';
			}
			$str .= '</tr>';
		}
		return $str;
	}

	function doesXmlCameraSizeDataExist($db,$cameraId, $tableName, $w, $h) {
		$picSizeId = findPicSizeId($db, $w, $h);
		if ($picSizeId < 0) {
			return false;
		} else {
			$stmt = $db->prepare('SELECT * from ' . $tableName . ' where
				    (camera_id		=:camera_id)
				and (picsize_id		=:picsize_id)');
			$stmt->bindValue(':camera_id', 	intval($cameraId), 		SQLITE3_INTEGER);
			$stmt->bindValue(':picsize_id', intval($picSizeId), 	SQLITE3_INTEGER);
			$res = $stmt->execute();
			if ( ($res) && ($row = $res->fetchArray())) {
				return true;
			} else {
				return false;
			}
		}
	}

	function doesXmlCameraFormatDataExist($db,$cameraId, $tableName, $formatId) {
		$stmt = $db->prepare('SELECT id FROM picformats WHERE (id=:id)');
		$stmt->bindValue(':id', intval($formatId), SQLITE3_INTEGER);
		$qr = $stmt->execute();
		if ( ($qr) && ($row = $qr->fetchArray())) {
			$stmt = $db->prepare('SELECT * from ' . $tableName . ' where
				    (camera_id		=:camera_id)
				and (format_id		=:format_id)');
			$stmt->bindValue(':camera_id', 	intval($cameraId), 		SQLITE3_INTEGER);
			$stmt->bindValue(':format_id', intval($formatId), 		SQLITE3_INTEGER);
			$res = $stmt->execute();
			if ( ($res) && ($row = $res->fetchArray())) {
				return true;
			} else {
				return false;
			}
		} else {
			return false;
		}
	}

	function doesXmlCameraDataExist($db,$idPi,$ci) {
		$stmt = $db->prepare('SELECT id from phone_cameras where
				(facing_id		=:facing_id)');
		$stmt->bindValue(':facing_id', 		intval($ci['facing_id']), 		SQLITE3_INTEGER);
		$res = $stmt->execute();
		if ( ($res) && ($row = $res->fetchArray())) {
			$cameraId = $row[0];
			
			$result = false;
			// Now check resolutions for this camera
			foreach ($ci->preview_size as $size) {
				$result = $result || doesXmlCameraSizeDataExist($db,$cameraId,'preview_sizes',$size['w'],$size['h']);
			}
			foreach ($ci->picture_size as $size) {
				$result = $result || doesXmlCameraSizeDataExist($db,$cameraId,'picture_sizes',$size['w'],$size['h']);
			}
			// Now check the formats for this camera
			foreach ($ci->preview_format as $fmt) {
				$result = $result || doesXmlCameraFormatDataExist($db,$cameraId,"preview_formats",$fmt['value']);
			}
			foreach ($ci->picture_format as $fmt) {
				$result = $result || doesXmlCameraFormatDataExist($db,$cameraId,"picture_formats",$fmt['value']);
			}
			return $result;
		} else {
			return false;
		}
	}

	function doesXmlPhoneDataExist($db,$pi) {
		// Get the phone info for this data
		$stmt = $db->prepare('SELECT id from phones where
			    (os_codename	=:os_codename)
			and (os_release		=:os_release)
			and (os_increment 	=:os_increment)
			and (device		 	=:device)
			and (model		 	=:model)
			and (product		=:product)
			and (product		=:product)
			and (manufacturer	=:manufacturer)
			and (brand			=:brand)
			');
			
		$manufacturer = isset($pi['manufacturer'])?$pi['manufacturer']:"";
		$brand = isset($pi['brand'])?$pi['brand']:"";
		$stmt->bindValue(':os_codename', 	$pi['os_codename'], 	SQLITE3_TEXT);
		$stmt->bindValue(':os_release', 	$pi['os_release'], 		SQLITE3_TEXT);
		$stmt->bindValue(':os_increment', 	$pi['os_increment'], 	SQLITE3_TEXT);
		$stmt->bindValue(':device', 		$pi['device'], 			SQLITE3_TEXT);
		$stmt->bindValue(':model',			$pi['model'], 			SQLITE3_TEXT);
		$stmt->bindValue(':product',		$pi['product'], 		SQLITE3_TEXT);
		$stmt->bindValue(':manufacturer',	$manufacturer, 			SQLITE3_TEXT);
		$stmt->bindValue(':brand',			$brand, 				SQLITE3_TEXT);
		$res = $stmt->execute();
		if ( ($res) && ($row = $res->fetchArray())) {
			$phoneId = $row[0];
			
			$result = false;
			foreach ($pi->camera_info as $ci) {
				$result = $result || doesXmlCameraDataExist($db, $phoneId, $ci);
			}
			return $result;
		} else {
			return false;
		}
		
	}

	function saveXmlPhoneData($db,$pi) {
		$manufacturer = isset($pi['manufacturer'])?$pi['manufacturer']:"";
		$brand = isset($pi['brand'])?$pi['brand']:"";
		$idPi=saveNewPhoneInfo($db,$pi['os_codename'],$pi['os_release'],$pi['os_increment'],$pi['device'],$pi['model'],$pi['product'], $manufacturer, $brand);
			
		foreach ($pi->camera_info as $ci) {
			$idCi = saveCameraInfo($db, $idPi, $ci['facing_id'], $ci['facing_string']);
			
			foreach ($ci->preview_size as $size) {
				saveCameraPreviewSize($db,$idCi,$size['w'],$size['h']);
			}
			foreach ($ci->picture_size as $size) {
				saveCameraPictureSize($db,$idCi,$size['w'],$size['h']);
			}
			foreach ($ci->preview_format as $fmt) {
				$name = isset($size['name'])?$size['name']:"";
				saveCameraPreviewFormat($db,$idCi,$fmt['value'],$name);
			}
			foreach ($ci->picture_format as $fmt) {
				$name = isset($size['name'])?$size['name']:"";
				saveCameraPreviewFormat($db,$idCi,$fmt['value'],$name);
			}
		}
	}
	
	
	function getDisplayTableData($db) {
		// Get the sizes table
		$sizesQuery = $db->prepare('SELECT id, w, h from picsizes ORDER BY w,h');
		$sizesResult = $sizesQuery->execute();
		if (!$sizesResult) return false;

		// Prepare a list of all sizes
		$sizesCount = 0;
		$sizesArray = [];
		while ($row = $sizesResult->fetchArray()) {
			 array_push($sizesArray, sprintf('(SELECT Count(*)>0 FROM %%s WHERE %%s.picsize_id=%d and %%s.camera_id=phone_cameras.id) as %%s%dx%d', $row[0],$row[1],$row[2]));
			 $sizesCount++;
		}
		
		// Prepare the large uber query
		$uberQuery = "select
			os_codename		
			,os_release	 	
			,os_increment 	
			,device		 	
			,model		 	
			,product	
			,manufacturer	
			,brand	
			,facing_id";
			
		$typesArray = array('preview_sizes'=>'v', 'picture_sizes'=>'p');
		foreach ($typesArray as $tableName => $fieldPrefix) {
			//~ print "<p/>$tableName";
			foreach ($sizesArray as $sizeString) {
				$uberQuery .= "\n," . sprintf($sizeString, $tableName, $tableName, $tableName, $fieldPrefix);
			}
		}
		$uberQuery .= "\n from phones left outer join phone_cameras on phones.id==phone_cameras.phone_id";
		//~ print "<pre>$uberQuery</pre>";
		//~ return $uberQuery;
		$result = '<table class="bordertable">';
		
		$leftHeaders = 0;
		$result .= "<tr>";
		$result .= '<th rowspan="2">Codename</th>'				;	$leftHeaders++;
		$result .= '<th rowspan="2">Release</th>'				;	$leftHeaders++;
		$result .= '<th rowspan="2">Increment</th>'				;	$leftHeaders++;
		$result .= '<th rowspan="2">Device</th>'				;	$leftHeaders++;
		$result .= '<th rowspan="2">Model</th>'					;	$leftHeaders++;
		$result .= '<th rowspan="2">Product</th>'				;	$leftHeaders++;
		$result .= '<th rowspan="2">Make</th>'					;	$leftHeaders++;
		$result .= '<th rowspan="2">Brand</th>'					;	$leftHeaders++;
		$result .= '<th rowspan="2">Camera<br/>Facing</th>'		;	$leftHeaders++;
		$result .= sprintf('<th colspan="%d">Preview</th>', $sizesCount);
		$result .= sprintf('<th colspan="%d">Pictures</th>', $sizesCount);
		$result .= "</tr>\n";

		$sizesResult = $sizesQuery->execute();
		if (!$sizesResult) return false;
		$sizesHeaders="";
		while ($row = $sizesResult->fetchArray()) {
			 $sizesHeaders .= sprintf('<th class="vert"><div>%dx%d</div></th>', $row[1],$row[2]);
		}

		$result .= "<tr>";
		$result .= $sizesHeaders;
		$result .= $sizesHeaders;
		$result .= "</tr>\n";

		$uqRes = $db->query($uberQuery);
		while ($row = $uqRes->fetchArray(SQLITE3_NUM)) {
			$result .= "\n<tr>";
			for ($i=0; $i<count($row); $i++) {
				if ($i<$leftHeaders) {
					// Rows before checkmarks
					$result .= '<td>' . $row[$i] . '</td>';
				} else {
					// Checkmarks
					if ($row[$i]>0) {
						$result .= "<td>&#x2713;</td>";
					} else {
						$result .= "<td></td>";
					}
				}
				
			}
			$result .= "</tr>";
		}

		//~ $result .= getQueryAsHtmlTableRows($db, $uberQuery);
		
		$result .= "</table>";
		return $result;

	}
	
	$dbFilePath = getDbFilePath();
	$db = new SQLite3($dbFilePath);
	ensureDbUpToDate($db);

	// Parse XML
	if (isset($_POST['xmldata'])) {
		$output = 
			print_r ( $_REQUEST, true )
			. print_r ( getallheaders (  ), true )
			. print_r ( $_SERVER, true )
			//~ . print_r ( $_FILES, true )
			;
			
		//~ file_put_contents ( './db/output.txt',  $output);
	
		$pi = new SimpleXMLElement($_POST['xmldata']);
		if (doesXmlPhoneDataExist($db, $pi)) {
			http_response_code (202);
			printf("Verified data, thank you!");
		} else {
			saveXmlPhoneData($db, $pi);
			http_response_code (202);
			printf("Data saved, thank you!");
		}
	} else {
?>
<html>
<head>
	<title>Android Reporter</title>
	<style>
		body 	{ font-family: Georgia; } 	
		h1, h2, h3, h4, h5	{ font-family: Arial; } 	
		.quote	{ 
			background: #E0E0E0; 
			padding-top: 0.5em;
			padding-bottom: 0.5em;
		}
		span.code {	font-family: Courier, "Courier New"; } 
		span.filename {	font-family:monospace;  font-weight:bold; }
		P.code 	{	font-family: Courier, "Courier New"; font-size:0.85em;} 	
		P.quote	{	font-style:italic; }
		col.centered { text-align:center; }
		h1 { font-size: 20px; }
		h2 { font-size: 18px; }
		h3 { font-size: 16px; }
		ul, ol  { padding-top: 0.25em; }
		li { padding-top: 0.25em; padding-bottom: 0.25em; }
		svg { padding-top: 1em; padding-bottom: 1em; }
		pre {  tab-interval:  0.25in }
		dt { 
			font-weight:bold; 
			padding-top: 0.5em;
			padding-bottom: 0.5em;
			margin-left: 1em;
			
		}

		table.bordertable, table.bordertable td {
			border: 1px dotted #808080;
			border-collapse: collapse;
		}

		/* Styling for the autogenerated TOC */
		div#toc li { 
			padding: 0.125em 0 0.125em 0; 
			margin: 0 0 0 0; 
			list-style-type: none;
			font-size: 0.75em;
			font-family: Arial, Helvetica, Sans-serif;
		}
		div#toc ul { 
			padding: 0em 0em 0em 2em;
			margin: 0 0 0 0;
		}
		div#toc a, div#toc a:visited { 
			text-decoration: none;
			color: #000000;
		}
		div#toc a:hover { 
			text-decoration: underline;
		}
	
		table.bordertable, table.bordertable td, table.bordertable th {
			border: 1px dotted #808080;
			border-collapse: collapse;
			font-family: Arial, Helvetica;
			font-size: 12px;
			vertical-align: bottom;
		}
		th.vert {
			height: 120px;
		}
		th.vert div {
				writing-mode: tb-rl;
				-webkit-transform: rotate(-90deg);
				-moz-transform: rotate(-90deg);
				-ms-transform: rotate(-90deg);
				-o-transform: rotate(-90deg);
				transform: rotate(-90deg); 
                width: 12px;
                margin-bottom: 10px;
                white-space: nowrap;
/*		
                padding-bottom: 10px;
                height: 100px;
                text-align: center;
                width: 10px;
                height: 100px;
                -webkit-transform: rotate(-90deg); 
                -moz-transform: rotate(-90deg);    
*/				
		}
		
	</style>
</head>
<body>
<h1>The Android Reporter project</h1>
<p/>To my knowledge, there is little information on 
which phones/cameras support what resolutions 
(see <a href="http://stackoverflow.com/questions/18515481/list-of-commonly-supported-camera-picture-sizes">here</a>)
This project is an attempt to gather this information.

<p/>How to help
<ol>
	<li/><a href="http://play.google.com/store/apps/details?id=com.atlarge.androidreporter">Install the app</a>
	<li/>Run the app
	<li/>View the results below

</ol>

<p/>You can find the source code for the app <a href="https://github.com/angelatlarge/AndroidReporter">here</a>
<p/>Results so far:
<?php
		//~ phpinfo();
		//~ printf("<p/>Showing old data...\n");
		//~ print(getQueryAsHtmlTable($db, 'select * from phones'));
		print("\n");
		print(getDisplayTableData($db));
?>
</body>
</html>
<?php
	}
?>
