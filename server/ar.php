
<?php
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
			product		 	TEXT)"
			);
		createTable($db, "phone_cameras", "
			(id integer PRIMARY KEY AUTOINCREMENT, 
			phone_id		INTEGER, 
			facing_id		INTEGER, 
			facing_string	TEXT)"
			);
		createTable($db, "resolutions", "
			(id integer PRIMARY KEY AUTOINCREMENT, 
			w		INTEGER, 
			h		INTEGER)"
			);
		createTable($db, "formats", "
			(id integer PRIMARY KEY, 
			name		TEXT)"
			);
		createTable($db, "preview_resolutions", "
			(camera_id integer,
			resolution_id integer, 
			PRIMARY KEY (camera_id, resolution_id) )"
			);
		createTable($db, "picture_resolutions", "
			(camera_id integer, 
			resolution_id integer, 
			PRIMARY KEY (camera_id, resolution_id) )"
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
	
	function saveNewPhoneInfo($db,$os_codename,$os_release,$os_increment,$device,$model,$product) {
			$stmt = $db->prepare('INSERT INTO phones
				(os_codename		
				,os_release	 	
				,os_increment 	
				,device		 	
				,model		 	
				,product)
			VALUES
				(:os_codename
				,:os_release
				,:os_increment
				,:device
				,:model
				,:product
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
				$stmt->bindValue(':phone_id', 		$idPi, 			SQLITE3_INTEGER);
				$stmt->bindValue(':facing_id', 		$facingId, 		SQLITE3_INTEGER);
				$stmt->bindValue(':facing_string', 	$facingString,	SQLITE3_TEXT);
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

	function getSizeId($db, $w, $h) {
		$stmt = $db->prepare('SELECT id FROM resolutions WHERE (w=:w) AND (h=:h)');
		$stmt->bindValue(':w', $w, SQLITE3_INTEGER);
		$stmt->bindValue(':h', $h, SQLITE3_INTEGER);
		$qr = $stmt.execute();
		if ( ($qr) && ($row = $qr->fetchArray())) {
			return $row[0];
		} else {
			$stmt = $db->prepare('INSERT INTO resolutions (w,h) values (:w. :h)');
			$stmt->bindValue(':w', $w, SQLITE3_INTEGER);
			$stmt->bindValue(':h', $h, SQLITE3_INTEGER);
			if (! $stmt.execute() ) {
				// Insert failed
				return -1;
			} else {
				return $db->lastInsertRowid();
			}
		}
	}

	function getQueryTable($db, $sql) {
		$str = '<table>';
		$results = $db->query($sql);
		while ($row = $results->fetchArray(SQLITE3_ASSOC)) {
			$str .= '<tr>';
			foreach ($row as $d) {
				$str .= '<td>' . $d . '</td>';
			}
			$str .= '</tr>';
		}
		return $str;
	}
	
	$db = new SQLite3('./db/ar.db');
	createAllTables($db);

	// Parse XML
	if (isset($_POST['xmldata'])) {
		printf("<p/>Saving new data...\n");
		$output = 
			print_r ( $_REQUEST, true )
			. print_r ( getallheaders (  ), true )
			. print_r ( $_SERVER, true )
			//~ . print_r ( $_FILES, true )
			;
			
		file_put_contents ( './db/output.txt',  $output);
	
		$pi = new SimpleXMLElement($_POST['xmldata']);
		$idPi=saveNewPhoneInfo($db,$pi['os_codename'],$pi['os_release'],$pi['os_increment'],$pi['device'],$pi['model'],$pi['product']);
			
		foreach ($pi->camera_info as $ci) {
			saveCameraInfo($db, $idPi, $ci['facing_id'], $ci['facing_string']);
			foreach ($ci->preview_size as $size) {
			}
			foreach ($ci->picture_size as $size) {
			}
			foreach ($ci->preview_format as $fmt) {
			}
			foreach ($ci->picture_format as $fmt) {
			}
			
		}
	} else {
		//~ phpinfo();
		printf("<p/>Showing old data...\n");
		print(getQueryTable($db, 'select * from phones'));
	}

	echo "<p/>done";
?>

